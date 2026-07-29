from __future__ import annotations

from dataclasses import dataclass
from typing import Any
from uuid import UUID


@dataclass(frozen=True)
class RequestContext:
    user_id: str
    workspace_id: str


@dataclass(frozen=True)
class ParsedChunk:
    id: UUID
    index: int
    page_number: int
    section_name: str
    text: str


@dataclass(frozen=True)
class ExtractedNode:
    temp_id: str
    entity_type: str
    name: str
    properties: dict[str, Any]


@dataclass(frozen=True)
class ExtractedEdge:
    source_temp_id: str
    target_temp_id: str
    relation_type: str
    chunk_id: UUID
    page_number: int
    section_name: str
    evidence_quote: str


@dataclass(frozen=True)
class ValidatedChunkGraph:
    nodes: tuple[ExtractedNode, ...]
    edges: tuple[ExtractedEdge, ...]


@dataclass(frozen=True)
class BuildMessage:
    graph_id: UUID
    document_id: UUID
    version: int
    object_key: str
    extractor_version: str
