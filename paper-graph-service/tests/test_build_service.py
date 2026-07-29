from __future__ import annotations

import json
from uuid import UUID

from app.domain.models import BuildMessage, ParsedChunk
from app.domain.schema import ComputerPaperGraphSchema
from app.services.build_service import PaperGraphBuildService

GRAPH_ID = UUID("44444444-4444-4444-4444-444444444444")
DOCUMENT_ID = UUID("55555555-5555-5555-5555-555555555555")
CHUNK_ID = UUID("66666666-6666-6666-6666-666666666666")
CHUNK = ParsedChunk(
    id=CHUNK_ID,
    index=0,
    page_number=1,
    section_name="Abstract",
    text="We propose CodeGraph for vulnerability detection.",
)
MESSAGE = BuildMessage(
    graph_id=GRAPH_ID,
    document_id=DOCUMENT_ID,
    version=1,
    object_key=f"paper-graph/42/{DOCUMENT_ID}/1/source.pdf",
    extractor_version="computer-paper-v1",
)


def model_response(quote: str = CHUNK.text) -> str:
    return json.dumps(
        {
            "nodes": [
                {
                    "temp_id": "paper_1",
                    "name": "codegraph",
                    "properties": {},
                },
                {
                    "temp_id": "method_1",
                    "name": "CodeGraph",
                    "properties": {},
                },
            ],
            "edges": [
                {
                    "source": "paper_1",
                    "target": "method_1",
                    "type": "PROPOSES",
                    "evidence": {
                        "chunk_id": str(CHUNK_ID),
                        "page": 1,
                        "section": "Abstract",
                        "quote": quote,
                    },
                }
            ],
        }
    )


class FakeDatabase:
    def __init__(self) -> None:
        self.calls: list[str] = []
        self.failed: str | None = None
        self.persisted = None

    def begin_build(self, message):
        self.calls.append("PARSING")
        return {"filename": "codegraph.pdf"}

    def replace_chunks(self, document_id, chunks):
        self.calls.append("EXTRACTING")
        assert chunks == [CHUNK]

    def mark_validating(self, document_id):
        self.calls.append("VALIDATING")

    def persist_graph(self, document_id, graph_id, validated):
        self.calls.append("COMPLETED")
        self.persisted = validated

    def mark_document_failed(self, document_id, message):
        self.calls.append("FAILED")
        self.failed = message


class FakeStorage:
    def get_bytes(self, object_key):
        return b"%PDF-1.7"


class FakeParser:
    def parse(self, content):
        return [CHUNK]


class FakeModel:
    def __init__(self, response: str) -> None:
        self.response = response

    def extract(self, prompt):
        assert "computer-science" in prompt
        return self.response


def build_service(database: FakeDatabase, response: str) -> PaperGraphBuildService:
    return PaperGraphBuildService(
        database=database,
        object_storage=FakeStorage(),
        parser=FakeParser(),
        model_client=FakeModel(response),
        schema=ComputerPaperGraphSchema(),
    )


def test_build_transitions_through_all_states_and_persists_evidence() -> None:
    database = FakeDatabase()

    outcome = build_service(database, model_response()).build(MESSAGE)

    assert outcome.status == "COMPLETED"
    assert database.calls == ["PARSING", "EXTRACTING", "VALIDATING", "COMPLETED"]
    assert database.persisted[0][1].edges[0].evidence_quote == CHUNK.text


def test_invalid_evidence_records_failed_status_without_persisting() -> None:
    database = FakeDatabase()

    outcome = build_service(database, model_response("invented quote")).build(MESSAGE)

    assert outcome.status == "FAILED"
    assert database.calls == ["PARSING", "EXTRACTING", "VALIDATING", "FAILED"]
    assert "not an exact chunk substring" in database.failed
    assert database.persisted is None
