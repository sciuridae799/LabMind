from __future__ import annotations

import json
from uuid import UUID

import pytest

from app.domain.models import ParsedChunk
from app.domain.schema import (
    RELATION_ENDPOINTS,
    ComputerPaperGraphSchema,
    GraphValidationError,
)

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


def entity(name: str) -> dict:
    return {"name": name, "properties": {}}


def evidence(quote: str = QUOTE) -> dict:
    return {
        "chunk_id": str(CHUNK_ID),
        "page": 1,
        "section": "Abstract",
        "quote": quote,
    }


def empty_payload() -> dict:
    return {relation_type: [] for relation_type in RELATION_ENDPOINTS}


def valid_payload() -> dict:
    payload = empty_payload()
    payload["PROPOSES"] = [
        {"method": entity("PatchTST"), "evidence": evidence()}
    ]
    return payload


def test_constructs_fixed_nodes_and_edge_with_exact_evidence() -> None:
    result = ComputerPaperGraphSchema().validate_response(
        json.dumps(valid_payload()), "PatchTST.pdf", chunk()
    )

    assert [(node.entity_type, node.name) for node in result.nodes] == [
        ("Paper", "PatchTST.pdf"),
        ("Method", "PatchTST"),
    ]
    assert result.edges[0].relation_type == "PROPOSES"
    assert result.edges[0].source_temp_id == "paper_1"
    assert result.edges[0].evidence_quote == QUOTE


def test_constructs_all_node_types_from_fixed_relation_slots() -> None:
    payload = valid_payload()
    payload["SOLVES"] = [
        {
            "method": entity("PatchTST"),
            "task": entity("Forecasting"),
            "evidence": evidence(),
        }
    ]
    payload["USES"] = [
        {
            "method": entity("PatchTST"),
            "dataset": entity("ETTm1"),
            "evidence": evidence(),
        }
    ]
    payload["ACHIEVES"] = [
        {
            "method": entity("PatchTST"),
            "metric_result": entity("MSE 0.321"),
            "evidence": evidence(),
        }
    ]
    payload["OUTPERFORMS"] = [
        {
            "method": entity("PatchTST"),
            "baseline": entity("DLinear"),
            "evidence": evidence(),
        }
    ]
    payload["HAS_LIMITATION"] = [
        {
            "method": entity("PatchTST"),
            "limitation": entity("Long input"),
            "evidence": evidence(),
        }
    ]

    result = ComputerPaperGraphSchema().validate_response(
        json.dumps(payload), "PatchTST.pdf", chunk()
    )

    assert {node.entity_type for node in result.nodes} == {
        "Paper",
        "Method",
        "Task",
        "Dataset",
        "MetricResult",
        "Baseline",
        "Limitation",
    }
    assert {edge.relation_type for edge in result.edges} == set(RELATION_ENDPOINTS)


def test_rejects_relation_item_with_the_wrong_entity_slots() -> None:
    payload = empty_payload()
    payload["OUTPERFORMS"] = [
        {
            "method": entity("PatchTST"),
            "task": entity("DLinear"),
            "evidence": evidence(),
        }
    ]

    with pytest.raises(GraphValidationError, match="invalid shape"):
        ComputerPaperGraphSchema().validate_response(
            json.dumps(payload), "PatchTST.pdf", chunk()
        )


def test_rejects_the_old_model_selected_nodes_and_edges_contract() -> None:
    payload = {"nodes": [], "edges": []}

    with pytest.raises(GraphValidationError, match="six relation arrays"):
        ComputerPaperGraphSchema().validate_response(
            json.dumps(payload), "PatchTST.pdf", chunk()
        )


def test_rejects_quote_that_is_not_in_chunk() -> None:
    payload = valid_payload()
    payload["PROPOSES"][0]["evidence"] = evidence("invented quote")

    with pytest.raises(GraphValidationError, match="not an exact chunk substring"):
        ComputerPaperGraphSchema().validate_response(
            json.dumps(payload), "PatchTST.pdf", chunk()
        )


def test_rejects_unknown_relation_array() -> None:
    payload = valid_payload()
    payload["MENTIONS"] = []

    with pytest.raises(GraphValidationError, match="six relation arrays"):
        ComputerPaperGraphSchema().validate_response(
            json.dumps(payload), "PatchTST.pdf", chunk()
        )


def test_rejects_duplicate_evidenced_relation() -> None:
    payload = valid_payload()
    payload["PROPOSES"].append(payload["PROPOSES"][0])

    with pytest.raises(GraphValidationError, match="duplicate PROPOSES"):
        ComputerPaperGraphSchema().validate_response(
            json.dumps(payload), "PatchTST.pdf", chunk()
        )


def test_rejects_markdown_wrapped_json_instead_of_repairing_it() -> None:
    raw = "```json\n" + json.dumps(valid_payload()) + "\n```"

    with pytest.raises(GraphValidationError, match="not valid JSON"):
        ComputerPaperGraphSchema().validate_response(raw, "PatchTST.pdf", chunk())
