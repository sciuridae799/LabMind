from __future__ import annotations

import json
from contextlib import contextmanager
from datetime import datetime, timezone
from typing import Any, Iterator, Sequence
from uuid import UUID, uuid4

import psycopg
from psycopg import Connection
from psycopg.rows import dict_row

from app.domain.models import ParsedChunk, RequestContext, ValidatedChunkGraph
from app.domain.schema import normalize_entity_name

REQUIRED_TABLES = {
    "paper_graph",
    "paper_graph_document",
    "paper_graph_chunk",
    "paper_graph_node",
    "paper_graph_edge",
}
REQUIRED_COLUMNS = {
    "paper_graph": {
        "id", "user_id", "workspace_id", "name", "description", "status",
        "created_at", "updated_at",
    },
    "paper_graph_document": {
        "id", "graph_id", "user_id", "filename", "object_key", "file_hash",
        "version", "status", "error_message", "extractor_version", "created_at",
        "updated_at",
    },
    "paper_graph_chunk": {
        "id", "document_id", "chunk_index", "page_number", "section_name",
        "text", "created_at",
    },
    "paper_graph_node": {
        "id", "graph_id", "entity_type", "name", "normalized_name",
        "properties_json", "created_at",
    },
    "paper_graph_edge": {
        "id", "graph_id", "source_node_id", "target_node_id", "relation_type",
        "document_id", "chunk_id", "page_number", "section_name",
        "evidence_quote", "created_at",
    },
}


class RecordNotFoundError(LookupError):
    pass


class StateConflictError(RuntimeError):
    pass


BUILDING_DOCUMENT_STATUSES = {"UPLOADED", "PARSING", "EXTRACTING", "VALIDATING"}


class PaperGraphDatabase:
    def __init__(self, dsn: str) -> None:
        self._dsn = dsn

    @contextmanager
    def connection(self) -> Iterator[Connection[dict[str, Any]]]:
        with psycopg.connect(self._dsn, row_factory=dict_row) as connection:
            yield connection

    def verify_schema(self) -> None:
        with self.connection() as connection:
            table_rows = connection.execute(
                """
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'public' AND table_name = ANY(%s)
                """,
                (list(REQUIRED_TABLES),),
            ).fetchall()
            column_rows = connection.execute(
                """
                SELECT table_name, column_name
                FROM information_schema.columns
                WHERE table_schema = 'public' AND table_name = ANY(%s)
                """,
                (list(REQUIRED_TABLES),),
            ).fetchall()
        actual_tables = {row["table_name"] for row in table_rows}
        missing_tables = REQUIRED_TABLES - actual_tables
        if missing_tables:
            raise RuntimeError(
                "paper graph database schema is missing tables: "
                + ", ".join(sorted(missing_tables))
            )
        actual_columns: dict[str, set[str]] = {table: set() for table in REQUIRED_TABLES}
        for row in column_rows:
            actual_columns[row["table_name"]].add(row["column_name"])
        missing_columns = {
            table: columns - actual_columns[table]
            for table, columns in REQUIRED_COLUMNS.items()
            if columns - actual_columns[table]
        }
        if missing_columns:
            details = "; ".join(
                f"{table}: {', '.join(sorted(columns))}"
                for table, columns in sorted(missing_columns.items())
            )
            raise RuntimeError(
                "paper graph database schema is missing columns: " + details
            )

    def create_graph(
        self,
        context: RequestContext,
        name: str,
        description: str | None,
    ) -> dict[str, Any]:
        graph_id = uuid4()
        now = datetime.now(timezone.utc)
        with self.connection() as connection:
            row = connection.execute(
                """
                INSERT INTO paper_graph (
                    id, user_id, workspace_id, name, description, status, created_at, updated_at
                ) VALUES (%s, %s, %s, %s, %s, 'ACTIVE', %s, %s)
                RETURNING id, name, description, status, created_at, updated_at
                """,
                (
                    graph_id,
                    context.user_id,
                    context.workspace_id,
                    name,
                    description,
                    now,
                    now,
                ),
            ).fetchone()
        assert row is not None
        return self._graph_response(row, 0, 0, 0, 0)

    def list_graphs(self, context: RequestContext) -> list[dict[str, Any]]:
        with self.connection() as connection:
            rows = connection.execute(
                """
                SELECT graph.id, graph.name, graph.description, graph.status,
                       graph.created_at, graph.updated_at,
                       (SELECT COUNT(*) FROM paper_graph_document document
                            WHERE document.graph_id = graph.id) AS document_count,
                       (SELECT COUNT(*) FROM paper_graph_document document
                            WHERE document.graph_id = graph.id
                              AND document.status = 'COMPLETED')
                           AS completed_document_count,
                       (SELECT COUNT(*) FROM paper_graph_node node
                            WHERE node.graph_id = graph.id) AS node_count,
                       (SELECT COUNT(*) FROM paper_graph_edge edge
                            WHERE edge.graph_id = graph.id) AS edge_count
                FROM paper_graph graph
                WHERE graph.user_id = %s AND graph.workspace_id = %s
                ORDER BY graph.created_at DESC
                """,
                (context.user_id, context.workspace_id),
            ).fetchall()
        return [
            self._graph_response(
                row,
                row["document_count"],
                row["completed_document_count"],
                row["node_count"],
                row["edge_count"],
            )
            for row in rows
        ]

    def get_graph(self, context: RequestContext, graph_id: UUID) -> dict[str, Any]:
        with self.connection() as connection:
            self._require_graph(connection, context, graph_id)
            row = connection.execute(
                """
                SELECT graph.id, graph.name, graph.description, graph.status,
                       graph.created_at, graph.updated_at,
                       (SELECT COUNT(*) FROM paper_graph_document d WHERE d.graph_id = graph.id)
                           AS document_count,
                       (SELECT COUNT(*) FROM paper_graph_document d
                            WHERE d.graph_id = graph.id AND d.status = 'COMPLETED')
                           AS completed_document_count,
                       (SELECT COUNT(*) FROM paper_graph_node n WHERE n.graph_id = graph.id)
                           AS node_count,
                       (SELECT COUNT(*) FROM paper_graph_edge e WHERE e.graph_id = graph.id)
                           AS edge_count
                FROM paper_graph graph
                WHERE graph.id = %s
                """,
                (graph_id,),
            ).fetchone()
        assert row is not None
        return self._graph_response(
            row,
            row["document_count"],
            row["completed_document_count"],
            row["node_count"],
            row["edge_count"],
        )

    def graph_object_keys(self, context: RequestContext, graph_id: UUID) -> list[str]:
        with self.connection() as connection:
            self._require_graph(connection, context, graph_id)
            rows = connection.execute(
                "SELECT object_key FROM paper_graph_document WHERE graph_id = %s",
                (graph_id,),
            ).fetchall()
        return [row["object_key"] for row in rows]

    def delete_graph(self, context: RequestContext, graph_id: UUID) -> None:
        with self.connection() as connection:
            self._require_graph(connection, context, graph_id, for_update=True)
            building_document = connection.execute(
                """
                SELECT id FROM paper_graph_document
                WHERE graph_id = %s AND status = ANY(%s)
                LIMIT 1
                """,
                (graph_id, list(BUILDING_DOCUMENT_STATUSES)),
            ).fetchone()
            if building_document is not None:
                raise StateConflictError(
                    "paper graph cannot be deleted while a document is being built"
                )
            connection.execute("DELETE FROM paper_graph WHERE id = %s", (graph_id,))

    def find_latest_document(
        self,
        context: RequestContext,
        graph_id: UUID,
        filename: str,
    ) -> dict[str, Any] | None:
        with self.connection() as connection:
            self._require_graph(connection, context, graph_id)
            return connection.execute(
                """
                SELECT document.*
                FROM paper_graph_document document
                WHERE document.graph_id = %s AND document.filename = %s
                ORDER BY document.version DESC
                LIMIT 1
                """,
                (graph_id, filename),
            ).fetchone()

    def find_matching_document(
        self,
        context: RequestContext,
        graph_id: UUID,
        filename: str,
        file_hash: str,
    ) -> dict[str, Any] | None:
        with self.connection() as connection:
            self._require_graph(connection, context, graph_id)
            return connection.execute(
                """
                SELECT document.*,
                       (SELECT COUNT(*) FROM paper_graph_chunk chunk
                        WHERE chunk.document_id = document.id) AS chunk_count,
                       (SELECT COUNT(*) FROM paper_graph_edge edge
                        WHERE edge.document_id = document.id) AS edge_count
                FROM paper_graph_document document
                WHERE document.graph_id = %s
                  AND document.filename = %s
                  AND document.file_hash = %s
                ORDER BY document.version DESC
                LIMIT 1
                """,
                (graph_id, filename, file_hash),
            ).fetchone()

    def insert_document(
        self,
        context: RequestContext,
        document_id: UUID,
        graph_id: UUID,
        filename: str,
        object_key: str,
        file_hash: str,
        version: int,
    ) -> dict[str, Any]:
        now = datetime.now(timezone.utc)
        with self.connection() as connection:
            self._require_graph(connection, context, graph_id, for_update=True)
            row = connection.execute(
                """
                INSERT INTO paper_graph_document (
                    id, graph_id, user_id, filename, object_key, file_hash, version,
                    status, error_message, extractor_version, created_at, updated_at
                ) VALUES (%s, %s, %s, %s, %s, %s, %s, 'UPLOADED', NULL, NULL, %s, %s)
                RETURNING *, 0 AS chunk_count, 0 AS edge_count
                """,
                (
                    document_id,
                    graph_id,
                    context.user_id,
                    filename,
                    object_key,
                    file_hash,
                    version,
                    now,
                    now,
                ),
            ).fetchone()
        assert row is not None
        return self._document_response(row, reused=False)

    def list_documents(
        self, context: RequestContext, graph_id: UUID
    ) -> list[dict[str, Any]]:
        with self.connection() as connection:
            self._require_graph(connection, context, graph_id)
            rows = connection.execute(
                """
                SELECT document.*,
                       (SELECT COUNT(*) FROM paper_graph_chunk chunk
                        WHERE chunk.document_id = document.id) AS chunk_count,
                       (SELECT COUNT(*) FROM paper_graph_edge edge
                        WHERE edge.document_id = document.id) AS edge_count
                FROM paper_graph_document document
                WHERE document.graph_id = %s
                ORDER BY document.created_at DESC
                """,
                (graph_id,),
            ).fetchall()
        return [self._document_response(row) for row in rows]

    def get_document(
        self, context: RequestContext, document_id: UUID, for_update: bool = False
    ) -> dict[str, Any]:
        suffix = " FOR UPDATE OF document" if for_update else ""
        with self.connection() as connection:
            row = connection.execute(
                """
                SELECT document.*,
                       (SELECT COUNT(*) FROM paper_graph_chunk chunk
                        WHERE chunk.document_id = document.id) AS chunk_count,
                       (SELECT COUNT(*) FROM paper_graph_edge edge
                        WHERE edge.document_id = document.id) AS edge_count
                FROM paper_graph_document document
                JOIN paper_graph graph ON graph.id = document.graph_id
                WHERE document.id = %s AND graph.user_id = %s AND graph.workspace_id = %s
                """
                + suffix,
                (document_id, context.user_id, context.workspace_id),
            ).fetchone()
        if row is None:
            raise RecordNotFoundError(f"paper document was not found: {document_id}")
        return row

    def document_response(
        self, row: dict[str, Any], reused: bool = False
    ) -> dict[str, Any]:
        return self._document_response(row, reused=reused)

    def mark_document_failed(self, document_id: UUID, error_message: str) -> None:
        with self.connection() as connection:
            result = connection.execute(
                """
                UPDATE paper_graph_document
                SET status = 'FAILED', error_message = %s, updated_at = CURRENT_TIMESTAMP
                WHERE id = %s
                """,
                (error_message, document_id),
            )
            if result.rowcount != 1:
                raise RecordNotFoundError(f"paper document was not found: {document_id}")

    def prepare_rebuild(self, context: RequestContext, document_id: UUID) -> dict[str, Any]:
        with self.connection() as connection:
            document = self._require_document(
                connection, context, document_id, for_update=True
            )
            if document["status"] in BUILDING_DOCUMENT_STATUSES:
                raise StateConflictError("paper document is currently being built")
            self._clear_document_results(connection, document_id, document["graph_id"])
            row = connection.execute(
                """
                UPDATE paper_graph_document
                SET status = 'UPLOADED', error_message = NULL, extractor_version = NULL,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = %s
                RETURNING *, 0 AS chunk_count, 0 AS edge_count
                """,
                (document_id,),
            ).fetchone()
        assert row is not None
        return row

    def delete_document(
        self, context: RequestContext, document_id: UUID
    ) -> dict[str, Any]:
        with self.connection() as connection:
            document = self._require_document(
                connection, context, document_id, for_update=True
            )
            if document["status"] in BUILDING_DOCUMENT_STATUSES:
                raise StateConflictError(
                    "paper document cannot be deleted while it is being built"
                )
            graph_id = document["graph_id"]
            connection.execute(
                "DELETE FROM paper_graph_document WHERE id = %s", (document_id,)
            )
            self._delete_orphan_nodes(connection, graph_id)
        return document

    def begin_build(self, message: Any) -> dict[str, Any] | None:
        with self.connection() as connection:
            document = connection.execute(
                "SELECT * FROM paper_graph_document WHERE id = %s FOR UPDATE",
                (message.document_id,),
            ).fetchone()
            if document is None:
                raise RecordNotFoundError(
                    f"paper document was not found: {message.document_id}"
                )
            expected = (
                document["graph_id"],
                document["version"],
                document["object_key"],
            )
            actual = (message.graph_id, message.version, message.object_key)
            if actual != expected:
                raise StateConflictError("paper graph build message does not match document metadata")
            if (
                document["status"] == "COMPLETED"
                and document["extractor_version"] == message.extractor_version
            ):
                return None
            row = connection.execute(
                """
                UPDATE paper_graph_document
                SET status = 'PARSING', error_message = NULL, extractor_version = %s,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = %s
                RETURNING *
                """,
                (message.extractor_version, message.document_id),
            ).fetchone()
        assert row is not None
        return row

    def replace_chunks(self, document_id: UUID, chunks: Sequence[ParsedChunk]) -> None:
        with self.connection() as connection:
            document = connection.execute(
                "SELECT graph_id FROM paper_graph_document WHERE id = %s FOR UPDATE",
                (document_id,),
            ).fetchone()
            if document is None:
                raise RecordNotFoundError(f"paper document was not found: {document_id}")
            self._clear_document_results(connection, document_id, document["graph_id"])
            now = datetime.now(timezone.utc)
            connection.executemany(
                """
                INSERT INTO paper_graph_chunk (
                    id, document_id, chunk_index, page_number, section_name, text, created_at
                ) VALUES (%s, %s, %s, %s, %s, %s, %s)
                """,
                [
                    (
                        chunk.id,
                        document_id,
                        chunk.index,
                        chunk.page_number,
                        chunk.section_name,
                        chunk.text,
                        now,
                    )
                    for chunk in chunks
                ],
            )
            connection.execute(
                """
                UPDATE paper_graph_document
                SET status = 'EXTRACTING', updated_at = CURRENT_TIMESTAMP
                WHERE id = %s
                """,
                (document_id,),
            )

    def mark_validating(self, document_id: UUID) -> None:
        self._set_status(document_id, "VALIDATING")

    def persist_graph(
        self,
        document_id: UUID,
        graph_id: UUID,
        chunk_graphs: Sequence[tuple[ParsedChunk, ValidatedChunkGraph]],
    ) -> None:
        edge_count = sum(len(graph.edges) for _, graph in chunk_graphs)
        if edge_count == 0:
            raise StateConflictError("paper graph extraction produced no evidenced edges")
        with self.connection() as connection:
            document = connection.execute(
                "SELECT graph_id FROM paper_graph_document WHERE id = %s FOR UPDATE",
                (document_id,),
            ).fetchone()
            if document is None or document["graph_id"] != graph_id:
                raise StateConflictError("document does not belong to the build graph")
            connection.execute(
                "DELETE FROM paper_graph_edge WHERE document_id = %s", (document_id,)
            )
            node_ids: dict[tuple[UUID, str], UUID] = {}
            now = datetime.now(timezone.utc)
            for chunk, chunk_graph in chunk_graphs:
                for node in chunk_graph.nodes:
                    row = connection.execute(
                        """
                        INSERT INTO paper_graph_node (
                            id, graph_id, entity_type, name, normalized_name,
                            properties_json, created_at
                        ) VALUES (%s, %s, %s, %s, %s, %s::jsonb, %s)
                        ON CONFLICT (graph_id, entity_type, normalized_name)
                        DO UPDATE SET
                            name = EXCLUDED.name,
                            properties_json = paper_graph_node.properties_json
                                || EXCLUDED.properties_json
                        RETURNING id
                        """,
                        (
                            uuid4(),
                            graph_id,
                            node.entity_type,
                            node.name,
                            normalize_entity_name(node.name),
                            json.dumps(node.properties, ensure_ascii=False),
                            now,
                        ),
                    ).fetchone()
                    assert row is not None
                    node_ids[(chunk.id, node.temp_id)] = row["id"]
            edge_rows: list[tuple[Any, ...]] = []
            for chunk, chunk_graph in chunk_graphs:
                for edge in chunk_graph.edges:
                    edge_rows.append(
                        (
                            uuid4(),
                            graph_id,
                            node_ids[(chunk.id, edge.source_temp_id)],
                            node_ids[(chunk.id, edge.target_temp_id)],
                            edge.relation_type,
                            document_id,
                            edge.chunk_id,
                            edge.page_number,
                            edge.section_name,
                            edge.evidence_quote,
                            now,
                        )
                    )
            connection.executemany(
                """
                INSERT INTO paper_graph_edge (
                    id, graph_id, source_node_id, target_node_id, relation_type,
                    document_id, chunk_id, page_number, section_name, evidence_quote,
                    created_at
                ) VALUES (%s, %s, %s, %s, %s, %s, %s, %s, %s, %s, %s)
                """,
                edge_rows,
            )
            self._delete_orphan_nodes(connection, graph_id)
            connection.execute(
                """
                UPDATE paper_graph_document
                SET status = 'COMPLETED', error_message = NULL,
                    updated_at = CURRENT_TIMESTAMP
                WHERE id = %s
                """,
                (document_id,),
            )

    def visualization(
        self,
        context: RequestContext,
        graph_id: UUID,
        document_id: UUID | None,
        entity_types: Sequence[str],
        query: str | None,
    ) -> dict[str, Any]:
        with self.connection() as connection:
            self._require_graph(connection, context, graph_id)
            if document_id is not None:
                document = self._require_document(connection, context, document_id)
                if document["graph_id"] != graph_id:
                    raise StateConflictError("document does not belong to graph")
            parameters: list[Any] = [graph_id]
            edge_document_clause = ""
            if document_id is not None:
                edge_document_clause = "AND edge.document_id = %s"
                parameters.append(document_id)
            node_type_clause = ""
            if entity_types:
                node_type_clause = "AND node.entity_type = ANY(%s)"
                parameters.append(list(entity_types))
            node_query_clause = ""
            if query is not None:
                node_query_clause = "AND node.name ILIKE %s"
                parameters.append(f"%{query}%")
            node_rows = connection.execute(
                f"""
                SELECT DISTINCT node.id, node.name, node.entity_type, node.properties_json
                FROM paper_graph_node node
                WHERE node.graph_id = %s
                  AND EXISTS (
                      SELECT 1 FROM paper_graph_edge edge
                      WHERE edge.graph_id = node.graph_id
                        AND (edge.source_node_id = node.id OR edge.target_node_id = node.id)
                        {edge_document_clause}
                  )
                  {node_type_clause}
                  {node_query_clause}
                ORDER BY node.name, node.id
                LIMIT 200
                """,
                parameters,
            ).fetchall()
            node_ids = [row["id"] for row in node_rows]
            edge_rows: list[dict[str, Any]] = []
            if node_ids:
                edge_parameters: list[Any] = [graph_id, node_ids, node_ids]
                document_clause = ""
                if document_id is not None:
                    document_clause = "AND edge.document_id = %s"
                    edge_parameters.append(document_id)
                edge_rows = connection.execute(
                    f"""
                    SELECT edge.id, edge.source_node_id, edge.target_node_id,
                           edge.relation_type, edge.document_id
                    FROM paper_graph_edge edge
                    WHERE edge.graph_id = %s
                      AND edge.source_node_id = ANY(%s)
                      AND edge.target_node_id = ANY(%s)
                      {document_clause}
                    ORDER BY edge.created_at, edge.id
                    LIMIT 400
                    """,
                    edge_parameters,
                ).fetchall()
        return self._visualization_response(node_rows, edge_rows)

    def node_detail(
        self, context: RequestContext, graph_id: UUID, node_id: UUID
    ) -> dict[str, Any]:
        with self.connection() as connection:
            self._require_graph(connection, context, graph_id)
            node = connection.execute(
                """
                SELECT id, name, entity_type, properties_json
                FROM paper_graph_node
                WHERE id = %s AND graph_id = %s
                """,
                (node_id, graph_id),
            ).fetchone()
            if node is None:
                raise RecordNotFoundError(f"paper graph node was not found: {node_id}")
            documents = connection.execute(
                """
                SELECT DISTINCT document.id, document.filename, document.version
                FROM paper_graph_edge edge
                JOIN paper_graph_document document ON document.id = edge.document_id
                WHERE edge.graph_id = %s
                  AND (edge.source_node_id = %s OR edge.target_node_id = %s)
                ORDER BY document.filename, document.version
                """,
                (graph_id, node_id, node_id),
            ).fetchall()
        return {
            "id": str(node["id"]),
            "name": node["name"],
            "entityType": node["entity_type"],
            "properties": node["properties_json"],
            "sourceDocuments": [
                {
                    "documentId": str(document["id"]),
                    "filename": document["filename"],
                    "version": document["version"],
                }
                for document in documents
            ],
        }

    def neighbors(
        self, context: RequestContext, graph_id: UUID, node_id: UUID
    ) -> dict[str, Any]:
        with self.connection() as connection:
            self._require_graph(connection, context, graph_id)
            center = connection.execute(
                """
                SELECT id, name, entity_type, properties_json
                FROM paper_graph_node WHERE id = %s AND graph_id = %s
                """,
                (node_id, graph_id),
            ).fetchone()
            if center is None:
                raise RecordNotFoundError(f"paper graph node was not found: {node_id}")
            edge_rows = connection.execute(
                """
                SELECT id, source_node_id, target_node_id, relation_type, document_id
                FROM paper_graph_edge
                WHERE graph_id = %s AND (source_node_id = %s OR target_node_id = %s)
                ORDER BY created_at, id
                LIMIT 400
                """,
                (graph_id, node_id, node_id),
            ).fetchall()
            neighbor_ids = sorted(
                {
                    edge["target_node_id"]
                    if edge["source_node_id"] == node_id
                    else edge["source_node_id"]
                    for edge in edge_rows
                },
                key=str,
            )[:199]
            node_rows = [center]
            if neighbor_ids:
                node_rows.extend(
                    connection.execute(
                        """
                        SELECT id, name, entity_type, properties_json
                        FROM paper_graph_node WHERE id = ANY(%s)
                        ORDER BY name, id
                        """,
                        (neighbor_ids,),
                    ).fetchall()
                )
        allowed_node_ids = {node["id"] for node in node_rows}
        filtered_edges = [
            edge
            for edge in edge_rows
            if edge["source_node_id"] in allowed_node_ids
            and edge["target_node_id"] in allowed_node_ids
        ]
        return self._visualization_response(node_rows, filtered_edges)

    def edge_evidence(
        self, context: RequestContext, graph_id: UUID, edge_id: UUID
    ) -> dict[str, Any]:
        with self.connection() as connection:
            self._require_graph(connection, context, graph_id)
            row = connection.execute(
                """
                SELECT edge.id, edge.relation_type, edge.document_id, edge.chunk_id,
                       edge.page_number, edge.section_name, edge.evidence_quote,
                       source.name AS source_name, target.name AS target_name,
                       document.filename, document.version
                FROM paper_graph_edge edge
                JOIN paper_graph_node source ON source.id = edge.source_node_id
                JOIN paper_graph_node target ON target.id = edge.target_node_id
                JOIN paper_graph_document document ON document.id = edge.document_id
                WHERE edge.id = %s AND edge.graph_id = %s
                """,
                (edge_id, graph_id),
            ).fetchone()
        if row is None:
            raise RecordNotFoundError(f"paper graph edge was not found: {edge_id}")
        return {
            "id": str(row["id"]),
            "sourceName": row["source_name"],
            "targetName": row["target_name"],
            "relationType": row["relation_type"],
            "documentId": str(row["document_id"]),
            "filename": row["filename"],
            "version": row["version"],
            "chunkId": str(row["chunk_id"]),
            "pageNumber": row["page_number"],
            "sectionName": row["section_name"],
            "evidenceQuote": row["evidence_quote"],
        }

    def _set_status(self, document_id: UUID, status: str) -> None:
        with self.connection() as connection:
            result = connection.execute(
                """
                UPDATE paper_graph_document
                SET status = %s, updated_at = CURRENT_TIMESTAMP
                WHERE id = %s
                """,
                (status, document_id),
            )
            if result.rowcount != 1:
                raise RecordNotFoundError(f"paper document was not found: {document_id}")

    @staticmethod
    def _require_graph(
        connection: Connection[dict[str, Any]],
        context: RequestContext,
        graph_id: UUID,
        for_update: bool = False,
    ) -> dict[str, Any]:
        suffix = " FOR UPDATE" if for_update else ""
        row = connection.execute(
            """
            SELECT * FROM paper_graph
            WHERE id = %s AND user_id = %s AND workspace_id = %s
            """
            + suffix,
            (graph_id, context.user_id, context.workspace_id),
        ).fetchone()
        if row is None:
            raise RecordNotFoundError(f"paper graph was not found: {graph_id}")
        return row

    @staticmethod
    def _require_document(
        connection: Connection[dict[str, Any]],
        context: RequestContext,
        document_id: UUID,
        for_update: bool = False,
    ) -> dict[str, Any]:
        suffix = " FOR UPDATE OF document" if for_update else ""
        row = connection.execute(
            """
            SELECT document.*
            FROM paper_graph_document document
            JOIN paper_graph graph ON graph.id = document.graph_id
            WHERE document.id = %s AND graph.user_id = %s AND graph.workspace_id = %s
            """
            + suffix,
            (document_id, context.user_id, context.workspace_id),
        ).fetchone()
        if row is None:
            raise RecordNotFoundError(f"paper document was not found: {document_id}")
        return row

    def _clear_document_results(
        self,
        connection: Connection[dict[str, Any]],
        document_id: UUID,
        graph_id: UUID,
    ) -> None:
        connection.execute(
            "DELETE FROM paper_graph_edge WHERE document_id = %s", (document_id,)
        )
        connection.execute(
            "DELETE FROM paper_graph_chunk WHERE document_id = %s", (document_id,)
        )
        self._delete_orphan_nodes(connection, graph_id)

    @staticmethod
    def _delete_orphan_nodes(
        connection: Connection[dict[str, Any]], graph_id: UUID
    ) -> None:
        connection.execute(
            """
            DELETE FROM paper_graph_node node
            WHERE node.graph_id = %s
              AND NOT EXISTS (
                  SELECT 1 FROM paper_graph_edge edge
                  WHERE edge.source_node_id = node.id OR edge.target_node_id = node.id
              )
            """,
            (graph_id,),
        )

    @staticmethod
    def _graph_response(
        row: dict[str, Any],
        document_count: int,
        completed_document_count: int,
        node_count: int,
        edge_count: int,
    ) -> dict[str, Any]:
        return {
            "id": str(row["id"]),
            "name": row["name"],
            "description": row["description"],
            "status": row["status"],
            "documentCount": document_count,
            "completedDocumentCount": completed_document_count,
            "nodeCount": node_count,
            "edgeCount": edge_count,
            "createdAt": row["created_at"],
            "updatedAt": row["updated_at"],
        }

    @staticmethod
    def _document_response(
        row: dict[str, Any], reused: bool = False
    ) -> dict[str, Any]:
        return {
            "id": str(row["id"]),
            "graphId": str(row["graph_id"]),
            "filename": row["filename"],
            "version": row["version"],
            "status": row["status"],
            "errorMessage": row["error_message"],
            "chunkCount": row["chunk_count"],
            "edgeCount": row["edge_count"],
            "reused": reused,
            "createdAt": row["created_at"],
            "updatedAt": row["updated_at"],
        }

    @staticmethod
    def _visualization_response(
        node_rows: Sequence[dict[str, Any]],
        edge_rows: Sequence[dict[str, Any]],
    ) -> dict[str, Any]:
        return {
            "nodes": [
                {
                    "id": str(node["id"]),
                    "name": node["name"],
                    "entityType": node["entity_type"],
                    "properties": node["properties_json"],
                }
                for node in node_rows
            ],
            "edges": [
                {
                    "id": str(edge["id"]),
                    "source": str(edge["source_node_id"]),
                    "target": str(edge["target_node_id"]),
                    "relationType": edge["relation_type"],
                    "documentId": str(edge["document_id"]),
                }
                for edge in edge_rows
            ],
            "nodeLimit": 200,
            "edgeLimit": 400,
        }
