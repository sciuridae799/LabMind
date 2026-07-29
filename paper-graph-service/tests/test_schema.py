from __future__ import annotations

import json
from uuid import UUID

import pytest

from app.domain.models import ParsedChunk
from app.domain.schema import ComputerPaperGraphSchema, GraphValidationError

CHUNK_ID = UUID("11111111-1111-1111-1111-111111111111")
QUOTE = "We propose PatchTST for long-term time series forecasting."


def chunk() -> ParsedChunk:
    return ParsedChunk(
        id=CHUNK_ID,
        index=0,
        page_number=1,
        section_name="Abstract",
        text=f"{QUOTE} It achieves an MSE of 0.321 on ETTm1.",
    )


def valid_payload() -> dict:
    return {
        "nodes": [
            {
                "temp_id": "paper_1",
                "type": "Paper",
                "name": "PatchTST.pdf",
                "properties": {},
            },
            {
                "temp_id": "method_1",
                "type": "Method",
                "name": "PatchTST",
                "properties": {"description": "Patch-based Transformer"},
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
                    "quote": QUOTE,
                },
            }
        ],
    }


def test_accepts_fixed_schema_with_exact_evidence() -> None:
    result = ComputerPaperGraphSchema().validate_response(
        json.dumps(valid_payload()), "PatchTST.pdf", chunk()
    )

    assert [node.entity_type for node in result.nodes] == ["Paper", "Method"]
    assert result.edges[0].relation_type == "PROPOSES"
    assert result.edges[0].evidence_quote == QUOTE


def test_rejects_relation_with_wrong_endpoint_types() -> None:
    payload = valid_payload()
    payload["nodes"][1]["type"] = "Dataset"

    with pytest.raises(GraphValidationError, match="PROPOSES requires Paper -> Method"):
        ComputerPaperGraphSchema().validate_response(
            json.dumps(payload), "PatchTST.pdf", chunk()
        )


def test_rejects_quote_that_is_not_in_chunk() -> None:
    payload = valid_payload()
    payload["edges"][0]["evidence"]["quote"] = "PatchTST is always the best model."

    with pytest.raises(GraphValidationError, match="not an exact chunk substring"):
        ComputerPaperGraphSchema().validate_response(
            json.dumps(payload), "PatchTST.pdf", chunk()
        )


def test_rejects_unknown_entity_type() -> None:
    payload = valid_payload()
    payload["nodes"][1]["type"] = "Architecture"

    with pytest.raises(GraphValidationError, match="unsupported entity type"):
        ComputerPaperGraphSchema().validate_response(
            json.dumps(payload), "PatchTST.pdf", chunk()
        )


def test_rejects_markdown_wrapped_json_instead_of_repairing_it() -> None:
    raw = "```json\n" + json.dumps(valid_payload()) + "\n```"

    with pytest.raises(GraphValidationError, match="not valid JSON"):
        ComputerPaperGraphSchema().validate_response(raw, "PatchTST.pdf", chunk())
