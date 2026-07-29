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
                "name": "PatchTST.pdf",
                "properties": {},
            },
            {
                "temp_id": "method_1",
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


def test_derives_all_node_types_from_relation_endpoint_roles() -> None:
    payload = valid_payload()
    payload["nodes"].extend(
        [
            {"temp_id": "task_1", "name": "Forecasting", "properties": {}},
            {"temp_id": "dataset_1", "name": "ETTm1", "properties": {}},
            {"temp_id": "metric_1", "name": "MSE 0.321", "properties": {}},
            {"temp_id": "baseline_1", "name": "DLinear", "properties": {}},
            {"temp_id": "limitation_1", "name": "Long input", "properties": {}},
        ]
    )
    evidence = {
        "chunk_id": str(CHUNK_ID),
        "page": 1,
        "section": "Abstract",
        "quote": QUOTE,
    }
    payload["edges"].extend(
        [
            {
                "source": "method_1",
                "target": "task_1",
                "type": "SOLVES",
                "evidence": evidence,
            },
            {
                "source": "method_1",
                "target": "dataset_1",
                "type": "USES",
                "evidence": evidence,
            },
            {
                "source": "method_1",
                "target": "metric_1",
                "type": "ACHIEVES",
                "evidence": evidence,
            },
            {
                "source": "method_1",
                "target": "baseline_1",
                "type": "OUTPERFORMS",
                "evidence": evidence,
            },
            {
                "source": "method_1",
                "target": "limitation_1",
                "type": "HAS_LIMITATION",
                "evidence": evidence,
            },
        ]
    )

    result = ComputerPaperGraphSchema().validate_response(
        json.dumps(payload), "PatchTST.pdf", chunk()
    )

    assert {node.temp_id: node.entity_type for node in result.nodes} == {
        "paper_1": "Paper",
        "method_1": "Method",
        "task_1": "Task",
        "dataset_1": "Dataset",
        "metric_1": "MetricResult",
        "baseline_1": "Baseline",
        "limitation_1": "Limitation",
    }


def test_rejects_a_node_used_in_incompatible_relation_roles() -> None:
    payload = valid_payload()
    payload["nodes"].append(
        {"temp_id": "method_2", "name": "DLinear", "properties": {}}
    )
    payload["edges"].append(
        {
            "source": "method_2",
            "target": "method_1",
            "type": "OUTPERFORMS",
            "evidence": {
                "chunk_id": str(CHUNK_ID),
                "page": 1,
                "section": "Abstract",
                "quote": QUOTE,
            },
        }
    )

    with pytest.raises(GraphValidationError, match="incompatible relation roles"):
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


def test_rejects_unknown_relation_type() -> None:
    payload = valid_payload()
    payload["edges"][0]["type"] = "MENTIONS"

    with pytest.raises(GraphValidationError, match="unsupported relation type"):
        ComputerPaperGraphSchema().validate_response(
            json.dumps(payload), "PatchTST.pdf", chunk()
        )


def test_rejects_the_removed_model_selected_node_type_field() -> None:
    payload = valid_payload()
    payload["nodes"][1]["type"] = "Method"

    with pytest.raises(GraphValidationError, match="invalid shape"):
        ComputerPaperGraphSchema().validate_response(
            json.dumps(payload), "PatchTST.pdf", chunk()
        )


def test_rejects_markdown_wrapped_json_instead_of_repairing_it() -> None:
    raw = "```json\n" + json.dumps(valid_payload()) + "\n```"

    with pytest.raises(GraphValidationError, match="not valid JSON"):
        ComputerPaperGraphSchema().validate_response(raw, "PatchTST.pdf", chunk())
