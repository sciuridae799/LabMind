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

        nodes = self._validate_nodes(raw_nodes, paper_name)
        node_by_temp_id = {node.temp_id: node for node in nodes}
        edges = self._validate_edges(raw_edges, node_by_temp_id, chunk)
        referenced_ids = {
            temp_id
            for edge in edges
            for temp_id in (edge.source_temp_id, edge.target_temp_id)
        }
        orphan_nodes = [
            node.temp_id
            for node in nodes
            if node.entity_type != "Paper" and node.temp_id not in referenced_ids
        ]
        if orphan_nodes:
            raise GraphValidationError(
                "non-Paper nodes must be referenced by an edge: "
                + ", ".join(orphan_nodes)
            )
        return ValidatedChunkGraph(nodes=tuple(nodes), edges=tuple(edges))

    def _validate_nodes(
        self,
        raw_nodes: list[Any],
        paper_name: str,
    ) -> list[ExtractedNode]:
        nodes: list[ExtractedNode] = []
        seen_temp_ids: set[str] = set()
        paper_count = 0
        for index, value in enumerate(raw_nodes):
            if not isinstance(value, dict) or set(value) != {
                "temp_id",
                "type",
                "name",
                "properties",
            }:
                raise GraphValidationError(f"nodes[{index}] has an invalid shape")
            temp_id = self._required_text(value["temp_id"], f"nodes[{index}].temp_id")
            entity_type = self._required_text(value["type"], f"nodes[{index}].type")
            name = self._required_text(value["name"], f"nodes[{index}].name")
            properties = value["properties"]
            if temp_id in seen_temp_ids:
                raise GraphValidationError(f"duplicate temp_id: {temp_id}")
            if entity_type not in ENTITY_TYPES:
                raise GraphValidationError(f"unsupported entity type: {entity_type}")
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
            if entity_type == "Paper":
                paper_count += 1
                if temp_id != "paper_1" or name != paper_name:
                    raise GraphValidationError(
                        "Paper node must use temp_id paper_1 and the supplied paper name"
                    )
            seen_temp_ids.add(temp_id)
            nodes.append(
                ExtractedNode(
                    temp_id=temp_id,
                    entity_type=entity_type,
                    name=name,
                    properties=properties,
                )
            )
        if paper_count != 1:
            raise GraphValidationError("each chunk response must contain exactly one Paper node")
        return nodes

    def _validate_edges(
        self,
        raw_edges: list[Any],
        node_by_temp_id: dict[str, ExtractedNode],
        chunk: ParsedChunk,
    ) -> list[ExtractedEdge]:
        edges: list[ExtractedEdge] = []
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
            actual_source = node_by_temp_id[source].entity_type
            actual_target = node_by_temp_id[target].entity_type
            if (actual_source, actual_target) != (expected_source, expected_target):
                raise GraphValidationError(
                    f"{relation_type} requires {expected_source} -> {expected_target}"
                )
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
        return edges

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
