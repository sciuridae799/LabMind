from __future__ import annotations

import hashlib
from datetime import datetime, timezone
from uuid import UUID

import pytest

from app.domain.models import RequestContext
from app.services.paper_graph_service import (
    EXTRACTOR_VERSION,
    MAX_PDF_FILE_SIZE_BYTES,
    PaperGraphService,
)

GRAPH_ID = UUID("22222222-2222-2222-2222-222222222222")
DOCUMENT_ID = UUID("33333333-3333-3333-3333-333333333333")
CONTEXT = RequestContext(user_id="42", workspace_id="workspace-1")
PDF = b"%PDF-1.7\ncomputer paper"


def document_row(file_hash: str, version: int = 1) -> dict:
    now = datetime.now(timezone.utc)
    return {
        "id": DOCUMENT_ID,
        "graph_id": GRAPH_ID,
        "filename": "paper.pdf",
        "object_key": f"paper-graph/42/{DOCUMENT_ID}/{version}/source.pdf",
        "file_hash": file_hash,
        "version": version,
        "status": "COMPLETED",
        "error_message": None,
        "chunk_count": 2,
        "edge_count": 3,
        "created_at": now,
        "updated_at": now,
    }


class FakeDatabase:
    def __init__(self) -> None:
        self.matching: dict | None = None
        self.latest: dict | None = None
        self.inserted: dict | None = None
        self.failed: tuple[UUID, str] | None = None

    def find_matching_document(self, context, graph_id, filename, file_hash):
        assert (context, graph_id, filename) == (CONTEXT, GRAPH_ID, "paper.pdf")
        return self.matching

    def find_latest_document(self, context, graph_id, filename):
        return self.latest

    def document_response(self, row, reused=False):
        return {"id": str(row["id"]), "version": row["version"], "reused": reused}

    def insert_document(self, **values):
        self.inserted = values
        return {"id": str(values["document_id"]), "version": values["version"], "reused": False}

    def mark_document_failed(self, document_id, message):
        self.failed = (document_id, message)


class FakeStorage:
    def __init__(self) -> None:
        self.puts: list[tuple[str, bytes]] = []
        self.removes: list[str] = []

    def put_pdf(self, key, content):
        self.puts.append((key, content))

    def remove(self, key):
        self.removes.append(key)


class FakeProducer:
    def __init__(self, error: Exception | None = None) -> None:
        self.messages = []
        self.error = error

    def publish(self, message):
        if self.error is not None:
            raise self.error
        self.messages.append(message)


def test_same_filename_and_hash_reuses_existing_version() -> None:
    database = FakeDatabase()
    database.matching = document_row(hashlib.sha256(PDF).hexdigest())
    storage = FakeStorage()
    producer = FakeProducer()
    service = PaperGraphService(database, storage, producer)

    result = service.upload_document(CONTEXT, GRAPH_ID, "paper.pdf", PDF)

    assert result == {"id": str(DOCUMENT_ID), "version": 1, "reused": True}
    assert storage.puts == []
    assert producer.messages == []


def test_changed_hash_creates_next_version_and_build_event() -> None:
    database = FakeDatabase()
    database.latest = document_row("different-hash", version=3)
    storage = FakeStorage()
    producer = FakeProducer()
    service = PaperGraphService(database, storage, producer)

    result = service.upload_document(CONTEXT, GRAPH_ID, "paper.pdf", PDF)

    assert result["version"] == 4
    assert database.inserted is not None
    assert database.inserted["version"] == 4
    assert database.inserted["object_key"].startswith("paper-graph/42/")
    assert database.inserted["object_key"].endswith("/4/source.pdf")
    assert producer.messages[0].extractor_version == EXTRACTOR_VERSION


def test_kafka_publish_failure_is_recorded_and_raised() -> None:
    database = FakeDatabase()
    storage = FakeStorage()
    producer = FakeProducer(RuntimeError("broker rejected message"))
    service = PaperGraphService(database, storage, producer)

    with pytest.raises(RuntimeError, match="Kafka publish failed"):
        service.upload_document(CONTEXT, GRAPH_ID, "paper.pdf", PDF)

    assert database.failed is not None
    assert "broker rejected message" in database.failed[1]


def test_oversized_pdf_is_rejected_before_storage() -> None:
    database = FakeDatabase()
    storage = FakeStorage()
    producer = FakeProducer()
    service = PaperGraphService(database, storage, producer)
    content = b"%PDF-" + b"x" * (MAX_PDF_FILE_SIZE_BYTES - len(b"%PDF-") + 1)

    with pytest.raises(ValueError, match="must not exceed 10 MB"):
        service.upload_document(CONTEXT, GRAPH_ID, "paper.pdf", content)

    assert storage.puts == []
    assert producer.messages == []
