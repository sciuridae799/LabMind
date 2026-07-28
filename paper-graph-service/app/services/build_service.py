from __future__ import annotations

import logging
from dataclasses import dataclass

from app.domain.models import BuildMessage
from app.domain.schema import ComputerPaperGraphSchema
from app.infrastructure.database import PaperGraphDatabase
from app.infrastructure.llm_client import GraphExtractionModelClient
from app.infrastructure.object_storage import PaperObjectStorage
from app.infrastructure.pdf_parser import ComputerPaperPdfParser

logger = logging.getLogger(__name__)
MAX_ERROR_MESSAGE_CHARACTERS = 4_000


@dataclass(frozen=True)
class BuildOutcome:
    status: str
    error_message: str | None


class PaperGraphBuildService:
    def __init__(
        self,
        database: PaperGraphDatabase,
        object_storage: PaperObjectStorage,
        parser: ComputerPaperPdfParser,
        model_client: GraphExtractionModelClient,
        schema: ComputerPaperGraphSchema,
    ) -> None:
        self._database = database
        self._object_storage = object_storage
        self._parser = parser
        self._model_client = model_client
        self._schema = schema

    def build(self, message: BuildMessage) -> BuildOutcome:
        document = self._database.begin_build(message)
        if document is None:
            return BuildOutcome(status="IDEMPOTENT", error_message=None)
        try:
            paper_name = document["filename"][:-4].strip()
            if not paper_name:
                raise ValueError("paper filename does not contain a paper name")
            content = self._object_storage.get_bytes(message.object_key)
            chunks = self._parser.parse(content)
            self._database.replace_chunks(message.document_id, chunks)

            raw_results = [
                (
                    chunk,
                    self._model_client.extract(
                        self._schema.build_prompt(paper_name, chunk)
                    ),
                )
                for chunk in chunks
            ]
            self._database.mark_validating(message.document_id)
            validated = [
                (
                    chunk,
                    self._schema.validate_response(
                        raw_response,
                        paper_name,
                        chunk,
                    ),
                )
                for chunk, raw_response in raw_results
            ]
            self._database.persist_graph(
                message.document_id,
                message.graph_id,
                validated,
            )
            return BuildOutcome(status="COMPLETED", error_message=None)
        except Exception as error:
            message_text = (
                f"{type(error).__name__}: {error}"
            )[:MAX_ERROR_MESSAGE_CHARACTERS]
            self._database.mark_document_failed(message.document_id, message_text)
            logger.exception(
                "Paper graph build failed for document %s", message.document_id
            )
            return BuildOutcome(status="FAILED", error_message=message_text)
