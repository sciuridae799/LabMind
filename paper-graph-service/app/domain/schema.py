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
        if not isinstance(payload, dict) or set(payload) != {"nodes", "edges"}:
            raise GraphValidationError("model response must contain only nodes and edges")
        raw_nodes = payload["nodes"]
        raw_edges = payload["edges"]
        if not isinstance(raw_nodes, list) or not isinstance(raw_edges, list):
            raise GraphValidationError("nodes and edges must be arrays")

        raw_node_by_temp_id = self._validate_nodes(raw_nodes, paper_name)
        edges, node_types = self._validate_edges(
            raw_edges, raw_node_by_temp_id, chunk
        )
        orphan_nodes = [
            temp_id
            for temp_id in raw_node_by_temp_id
            if temp_id != "paper_1" and temp_id not in node_types
        ]
        if orphan_nodes:
            raise GraphValidationError(
                "non-Paper nodes must be referenced by an edge: "
                + ", ".join(orphan_nodes)
            )
        nodes = [
            ExtractedNode(
                temp_id=temp_id,
                entity_type=node_types[temp_id],
                name=value["name"],
                properties=value["properties"],
            )
            for temp_id, value in raw_node_by_temp_id.items()
        ]
        return ValidatedChunkGraph(nodes=tuple(nodes), edges=tuple(edges))

    def _validate_nodes(
        self,
        raw_nodes: list[Any],
        paper_name: str,
    ) -> dict[str, dict[str, Any]]:
        nodes: dict[str, dict[str, Any]] = {}
        for index, value in enumerate(raw_nodes):
            if not isinstance(value, dict) or set(value) != {
                "temp_id",
                "name",
                "properties",
            }:
                raise GraphValidationError(f"nodes[{index}] has an invalid shape")
            temp_id = self._required_text(value["temp_id"], f"nodes[{index}].temp_id")
            name = self._required_text(value["name"], f"nodes[{index}].name")
            properties = value["properties"]
            if temp_id in nodes:
                raise GraphValidationError(f"duplicate temp_id: {temp_id}")
            if not isinstance(properties, dict):
                raise GraphValidationError(f"nodes[{index}].properties must be an object")
            if any(
                not isinstance(key, str)
                or not isinstance(property_value, (str, int, float, bool))
                for key, property_value in properties.items()
            ):
                raise GraphValidationError(
                    f"nodes[{index}].properties must contain scalar JSON values"
                )
            nodes[temp_id] = {"name": name, "properties": properties}
        if "paper_1" not in nodes or nodes["paper_1"]["name"] != paper_name:
            raise GraphValidationError("each chunk response must contain exactly one Paper node")
        return nodes

    def _validate_edges(
        self,
        raw_edges: list[Any],
        node_by_temp_id: dict[str, dict[str, Any]],
        chunk: ParsedChunk,
    ) -> tuple[list[ExtractedEdge], dict[str, str]]:
        edges: list[ExtractedEdge] = []
        node_types = {"paper_1": "Paper"}
        seen_edges: set[tuple[str, str, str, str]] = set()
        for index, value in enumerate(raw_edges):
            if not isinstance(value, dict) or set(value) != {
                "source",
                "target",
                "type",
                "evidence",
            }:
                raise GraphValidationError(f"edges[{index}] has an invalid shape")
            source = self._required_text(value["source"], f"edges[{index}].source")
            target = self._required_text(value["target"], f"edges[{index}].target")
            relation_type = self._required_text(value["type"], f"edges[{index}].type")
            evidence = value["evidence"]
            if source not in node_by_temp_id or target not in node_by_temp_id:
                raise GraphValidationError(f"edges[{index}] references an unknown node")
            if relation_type not in RELATION_ENDPOINTS:
                raise GraphValidationError(f"unsupported relation type: {relation_type}")
            expected_source, expected_target = RELATION_ENDPOINTS[relation_type]
            self._record_node_type(node_types, source, expected_source)
            self._record_node_type(node_types, target, expected_target)
            if not isinstance(evidence, dict) or set(evidence) != {
                "chunk_id",
                "page",
                "section",
                "quote",
            }:
                raise GraphValidationError(f"edges[{index}].evidence has an invalid shape")
            evidence_chunk_id = self._parse_uuid(
                evidence["chunk_id"], f"edges[{index}].evidence.chunk_id"
            )
            if evidence_chunk_id != chunk.id:
                raise GraphValidationError(f"edges[{index}] has the wrong chunk_id")
            if evidence["page"] != chunk.page_number:
                raise GraphValidationError(f"edges[{index}] has the wrong page")
            if evidence["section"] != chunk.section_name:
                raise GraphValidationError(f"edges[{index}] has the wrong section")
            quote = self._required_text(evidence["quote"], f"edges[{index}].evidence.quote")
            if quote not in chunk.text:
                raise GraphValidationError(
                    f"edges[{index}] evidence quote is not an exact chunk substring"
                )
            edge_key = (source, target, relation_type, quote)
            if edge_key in seen_edges:
                raise GraphValidationError(f"duplicate edge at edges[{index}]")
            seen_edges.add(edge_key)
            edges.append(
                ExtractedEdge(
                    source_temp_id=source,
                    target_temp_id=target,
                    relation_type=relation_type,
                    chunk_id=chunk.id,
                    page_number=chunk.page_number,
                    section_name=chunk.section_name,
                    evidence_quote=quote,
                )
            )
        return edges, node_types

    @staticmethod
    def _record_node_type(
        node_types: dict[str, str], temp_id: str, expected_type: str
    ) -> None:
        existing_type = node_types.get(temp_id)
        if existing_type is not None and existing_type != expected_type:
            raise GraphValidationError(
                f"node {temp_id} has incompatible relation roles: "
                f"{existing_type} and {expected_type}"
            )
        node_types[temp_id] = expected_type

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
