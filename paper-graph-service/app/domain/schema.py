from __future__ import annotations

import json
import re
import unicodedata
from pathlib import Path
from typing import Any
from uuid import UUID

from app.domain.models import (
    ExtractedEdge,
    ExtractedNode,
    ParsedChunk,
    ValidatedChunkGraph,
)

ENTITY_TYPES = frozenset(
    {
        "Paper",
        "Method",
        "Task",
        "Dataset",
        "MetricResult",
        "Baseline",
        "Limitation",
    }
)

RELATION_ENDPOINTS = {
    "PROPOSES": ("Paper", "Method"),
    "SOLVES": ("Method", "Task"),
    "USES": ("Method", "Dataset"),
    "ACHIEVES": ("Method", "MetricResult"),
    "OUTPERFORMS": ("Method", "Baseline"),
    "HAS_LIMITATION": ("Method", "Limitation"),
}

RELATION_TARGET_FIELDS = {
    "PROPOSES": "method",
    "SOLVES": "task",
    "USES": "dataset",
    "ACHIEVES": "metric_result",
    "OUTPERFORMS": "baseline",
    "HAS_LIMITATION": "limitation",
}

PROMPT_PATH = (
    Path(__file__).resolve().parent.parent
    / "prompts"
    / "computer_paper_graph.md"
)


class GraphValidationError(ValueError):
    pass


def normalize_entity_name(name: str) -> str:
    normalized = unicodedata.normalize("NFKC", name).casefold().strip()
    return re.sub(r"\s+", " ", normalized)


class ComputerPaperGraphSchema:
    def __init__(self) -> None:
        self._prompt_template = PROMPT_PATH.read_text(encoding="utf-8")

    def build_prompt(self, paper_name: str, chunk: ParsedChunk) -> str:
        metadata = json.dumps(
            {
                "paper_name": paper_name,
                "chunk_id": str(chunk.id),
                "page_number": chunk.page_number,
                "section_name": chunk.section_name,
            },
            ensure_ascii=False,
        )
        return f"{self._prompt_template}\n\nMetadata JSON:\n{metadata}\n\nChunk:\n{chunk.text}"

    def validate_response(
        self,
        raw_response: str,
        paper_name: str,
        chunk: ParsedChunk,
    ) -> ValidatedChunkGraph:
        try:
            payload = json.loads(raw_response)
        except json.JSONDecodeError as error:
            raise GraphValidationError("model response is not valid JSON") from error
        if not isinstance(payload, dict) or set(payload) != set(RELATION_ENDPOINTS):
            raise GraphValidationError(
                "model response must contain exactly the six relation arrays"
            )

        nodes = [
            ExtractedNode(
                temp_id="paper_1",
                entity_type="Paper",
                name=paper_name,
                properties={},
            )
        ]
        edges: list[ExtractedEdge] = []
        seen_edges: set[tuple[str, str, str, str]] = set()
        for relation_type, endpoint_types in RELATION_ENDPOINTS.items():
            relation_values = payload[relation_type]
            if not isinstance(relation_values, list):
                raise GraphValidationError(f"{relation_type} must be an array")
            for index, value in enumerate(relation_values):
                relation_nodes, edge = self._validate_relation(
                    relation_type,
                    endpoint_types,
                    index,
                    value,
                    paper_name,
                    chunk,
                )
                source_node, target_node = relation_nodes
                edge_key = (
                    relation_type,
                    normalize_entity_name(source_node.name),
                    normalize_entity_name(target_node.name),
                    edge.evidence_quote,
                )
                if edge_key in seen_edges:
                    raise GraphValidationError(
                        f"duplicate {relation_type} relation at index {index}"
                    )
                seen_edges.add(edge_key)
                if source_node.temp_id != "paper_1":
                    nodes.append(source_node)
                nodes.append(target_node)
                edges.append(edge)
        return ValidatedChunkGraph(nodes=tuple(nodes), edges=tuple(edges))

    def _validate_relation(
        self,
        relation_type: str,
        endpoint_types: tuple[str, str],
        index: int,
        value: Any,
        paper_name: str,
        chunk: ParsedChunk,
    ) -> tuple[tuple[ExtractedNode, ExtractedNode], ExtractedEdge]:
        target_field = RELATION_TARGET_FIELDS[relation_type]
        required_fields = {"method", "evidence"}
        if relation_type != "PROPOSES":
            required_fields.add(target_field)
        if not isinstance(value, dict) or set(value) != required_fields:
            raise GraphValidationError(
                f"{relation_type}[{index}] has an invalid shape"
            )

        source_type, target_type = endpoint_types
        relation_prefix = relation_type.casefold()
        if relation_type == "PROPOSES":
            source_node = ExtractedNode(
                temp_id="paper_1",
                entity_type="Paper",
                name=paper_name,
                properties={},
            )
        else:
            source_name, source_properties = self._validate_entity(
                value["method"], f"{relation_type}[{index}].method"
            )
            source_node = ExtractedNode(
                temp_id=f"{relation_prefix}_{index}_method",
                entity_type=source_type,
                name=source_name,
                properties=source_properties,
            )

        target_name, target_properties = self._validate_entity(
            value[target_field], f"{relation_type}[{index}].{target_field}"
        )
        target_node = ExtractedNode(
            temp_id=f"{relation_prefix}_{index}_{target_field}",
            entity_type=target_type,
            name=target_name,
            properties=target_properties,
        )
        quote = self._validate_evidence(
            value["evidence"], f"{relation_type}[{index}].evidence", chunk
        )
        edge = ExtractedEdge(
            source_temp_id=source_node.temp_id,
            target_temp_id=target_node.temp_id,
            relation_type=relation_type,
            chunk_id=chunk.id,
            page_number=chunk.page_number,
            section_name=chunk.section_name,
            evidence_quote=quote,
        )
        return (source_node, target_node), edge

    def _validate_entity(
        self, value: Any, field: str
    ) -> tuple[str, dict[str, Any]]:
        if not isinstance(value, dict) or set(value) != {"name", "properties"}:
            raise GraphValidationError(f"{field} has an invalid shape")
        name = self._required_text(value["name"], f"{field}.name")
        properties = value["properties"]
        if not isinstance(properties, dict):
            raise GraphValidationError(f"{field}.properties must be an object")
        if any(
            not isinstance(key, str)
            or not isinstance(property_value, (str, int, float, bool))
            for key, property_value in properties.items()
        ):
            raise GraphValidationError(
                f"{field}.properties must contain scalar JSON values"
            )
        return name, properties

    def _validate_evidence(
        self, value: Any, field: str, chunk: ParsedChunk
    ) -> str:
        if not isinstance(value, dict) or set(value) != {
            "chunk_id",
            "page",
            "section",
            "quote",
        }:
            raise GraphValidationError(f"{field} has an invalid shape")
        evidence_chunk_id = self._parse_uuid(
            value["chunk_id"], f"{field}.chunk_id"
        )
        if evidence_chunk_id != chunk.id:
            raise GraphValidationError(f"{field} has the wrong chunk_id")
        if value["page"] != chunk.page_number:
            raise GraphValidationError(f"{field} has the wrong page")
        if value["section"] != chunk.section_name:
            raise GraphValidationError(f"{field} has the wrong section")
        quote = self._required_text(value["quote"], f"{field}.quote")
        if quote not in chunk.text:
            raise GraphValidationError(
                f"{field} quote is not an exact chunk substring"
            )
        return quote

    @staticmethod
    def _required_text(value: Any, field: str) -> str:
        if not isinstance(value, str) or not value.strip():
            raise GraphValidationError(f"{field} must be a non-empty string")
        return value.strip()

    @staticmethod
    def _parse_uuid(value: Any, field: str) -> UUID:
        if not isinstance(value, str):
            raise GraphValidationError(f"{field} must be a UUID string")
        try:
            return UUID(value)
        except ValueError as error:
            raise GraphValidationError(f"{field} must be a UUID string") from error
