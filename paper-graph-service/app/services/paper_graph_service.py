from __future__ import annotations

import hashlib
from typing import Sequence
from uuid import UUID, uuid4

from app.domain.models import BuildMessage, RequestContext
from app.domain.schema import ENTITY_TYPES
from app.infrastructure.database import BUILDING_DOCUMENT_STATUSES, PaperGraphDatabase, StateConflictError
from app.infrastructure.kafka import BuildEventProducer
from app.infrastructure.object_storage import PaperObjectStorage

EXTRACTOR_VERSION = "computer-paper-v1"
MAX_PDF_FILE_SIZE_BYTES = 10 * 1024 * 1024
MAX_PDF_FILE_SIZE_MESSAGE = "uploaded PDF must not exceed 10 MB"


class PaperGraphService:
    def __init__(
        self,
        database: PaperGraphDatabase,
        object_storage: PaperObjectStorage,
        producer: BuildEventProducer,
    ) -> None:
        self._database = database
        self._object_storage = object_storage
        self._producer = producer

    def create_graph(
        self,
        context: RequestContext,
        name: str,
        description: str | None,
    ) -> dict:
        normalized_name = name.strip()
        normalized_description = description.strip() if description is not None else None
        return self._database.create_graph(
            context,
            normalized_name,
            normalized_description,
        )

    def list_graphs(self, context: RequestContext) -> list[dict]:
        return self._database.list_graphs(context)

    def get_graph(self, context: RequestContext, graph_id: UUID) -> dict:
        return self._database.get_graph(context, graph_id)

    def delete_graph(self, context: RequestContext, graph_id: UUID) -> None:
        documents = self._database.list_documents(context, graph_id)
        if any(document["status"] in BUILDING_DOCUMENT_STATUSES for document in documents):
            raise StateConflictError(
                "paper graph cannot be deleted while a document is being built"
            )
        object_keys = self._database.graph_object_keys(context, graph_id)
        for object_key in object_keys:
            self._object_storage.remove(object_key)
        self._database.delete_graph(context, graph_id)

    def upload_document(
        self,
        context: RequestContext,
        graph_id: UUID,
        filename: str,
        content: bytes,
    ) -> dict:
        normalized_filename = filename.strip()
        if not normalized_filename.lower().endswith(".pdf"):
            raise ValueError("paper graph only accepts PDF files")
        if not normalized_filename[:-4].strip():
            raise ValueError("uploaded PDF filename must contain a paper name")
        if len(normalized_filename) > 255:
            raise ValueError("uploaded PDF filename must not exceed 255 characters")
        if len(content) > MAX_PDF_FILE_SIZE_BYTES:
            raise ValueError(MAX_PDF_FILE_SIZE_MESSAGE)
        if not content:
            raise ValueError("uploaded PDF must not be empty")
        if not content.startswith(b"%PDF-"):
            raise ValueError("uploaded file is not a PDF")

        file_hash = hashlib.sha256(content).hexdigest()
        matching = self._database.find_matching_document(
            context,
            graph_id,
            normalized_filename,
            file_hash,
        )
        if matching is not None:
            return self._database.document_response(matching, reused=True)
        latest = self._database.find_latest_document(
            context,
            graph_id,
            normalized_filename,
        )
        version = latest["version"] + 1 if latest is not None else 1
        document_id = uuid4()
        object_key = (
            f"paper-graph/{context.user_id}/{document_id}/{version}/source.pdf"
        )

        self._object_storage.put_pdf(object_key, content)
        try:
            document = self._database.insert_document(
                context=context,
                document_id=document_id,
                graph_id=graph_id,
                filename=normalized_filename,
                object_key=object_key,
                file_hash=file_hash,
                version=version,
            )
        except Exception:
            self._object_storage.remove(object_key)
            raise
        self._publish_or_mark_failed(
            BuildMessage(
                graph_id=graph_id,
                document_id=document_id,
                version=version,
                object_key=object_key,
                extractor_version=EXTRACTOR_VERSION,
            )
        )
        return document

    def list_documents(
        self, context: RequestContext, graph_id: UUID
    ) -> list[dict]:
        return self._database.list_documents(context, graph_id)

    def document_status(self, context: RequestContext, document_id: UUID) -> dict:
        document = self._database.get_document(context, document_id)
        return self._database.document_response(document)

    def rebuild_document(self, context: RequestContext, document_id: UUID) -> dict:
        current = self._database.get_document(context, document_id)
        self._object_storage.stat(current["object_key"])
        document = self._database.prepare_rebuild(context, document_id)
        self._publish_or_mark_failed(
            BuildMessage(
                graph_id=document["graph_id"],
                document_id=document["id"],
                version=document["version"],
                object_key=document["object_key"],
                extractor_version=EXTRACTOR_VERSION,
            )
        )
        return self._database.document_response(document)

    def download_document(
        self, context: RequestContext, document_id: UUID
    ) -> tuple[str, bytes]:
        document = self._database.get_document(context, document_id)
        return document["filename"], self._object_storage.get_bytes(
            document["object_key"]
        )

    def delete_document(self, context: RequestContext, document_id: UUID) -> None:
        document = self._database.get_document(context, document_id)
        if document["status"] in BUILDING_DOCUMENT_STATUSES:
            raise StateConflictError(
                "paper document cannot be deleted while it is being built"
            )
        self._object_storage.remove(document["object_key"])
        self._database.delete_document(context, document_id)

    def visualization(
        self,
        context: RequestContext,
        graph_id: UUID,
        document_id: UUID | None,
        entity_types: Sequence[str],
        query: str | None,
    ) -> dict:
        unsupported = set(entity_types) - ENTITY_TYPES
        if unsupported:
            raise ValueError(
                "unsupported entity types: " + ", ".join(sorted(unsupported))
            )
        normalized_query = query.strip() if query is not None else None
        if normalized_query == "":
            normalized_query = None
        return self._database.visualization(
            context,
            graph_id,
            document_id,
            tuple(entity_types),
            normalized_query,
        )

    def node_detail(
        self, context: RequestContext, graph_id: UUID, node_id: UUID
    ) -> dict:
        return self._database.node_detail(context, graph_id, node_id)

    def neighbors(
        self, context: RequestContext, graph_id: UUID, node_id: UUID
    ) -> dict:
        return self._database.neighbors(context, graph_id, node_id)

    def edge_evidence(
        self, context: RequestContext, graph_id: UUID, edge_id: UUID
    ) -> dict:
        return self._database.edge_evidence(context, graph_id, edge_id)

    def _publish_or_mark_failed(self, message: BuildMessage) -> None:
        try:
            self._producer.publish(message)
        except Exception as error:
            failure = f"Kafka publish failed: {type(error).__name__}: {error}"
            self._database.mark_document_failed(message.document_id, failure)
            raise RuntimeError(failure) from error
