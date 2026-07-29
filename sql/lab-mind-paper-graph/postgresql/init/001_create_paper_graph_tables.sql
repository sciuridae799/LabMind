CREATE TABLE IF NOT EXISTS paper_graph (
    id UUID PRIMARY KEY,
    user_id VARCHAR(64) NOT NULL,
    workspace_id VARCHAR(64) NOT NULL,
    name VARCHAR(160) NOT NULL,
    description TEXT,
    status VARCHAR(16) NOT NULL CHECK (status IN ('ACTIVE')),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_paper_graph_owner
    ON paper_graph (workspace_id, user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS paper_graph_document (
    id UUID PRIMARY KEY,
    graph_id UUID NOT NULL REFERENCES paper_graph(id) ON DELETE CASCADE,
    user_id VARCHAR(64) NOT NULL,
    filename VARCHAR(255) NOT NULL,
    object_key VARCHAR(800) NOT NULL,
    file_hash CHAR(64) NOT NULL,
    version INTEGER NOT NULL CHECK (version > 0),
    status VARCHAR(16) NOT NULL CHECK (
        status IN ('UPLOADED', 'PARSING', 'EXTRACTING', 'VALIDATING', 'COMPLETED', 'FAILED')
    ),
    error_message TEXT,
    extractor_version VARCHAR(64),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    UNIQUE (graph_id, filename, version),
    UNIQUE (graph_id, filename, file_hash)
);

CREATE INDEX IF NOT EXISTS idx_paper_graph_document_graph
    ON paper_graph_document (graph_id, created_at DESC);

CREATE TABLE IF NOT EXISTS paper_graph_chunk (
    id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES paper_graph_document(id) ON DELETE CASCADE,
    chunk_index INTEGER NOT NULL CHECK (chunk_index >= 0),
    page_number INTEGER NOT NULL CHECK (page_number > 0),
    section_name VARCHAR(255) NOT NULL,
    text TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (document_id, chunk_index)
);

CREATE INDEX IF NOT EXISTS idx_paper_graph_chunk_document
    ON paper_graph_chunk (document_id, chunk_index);

CREATE TABLE IF NOT EXISTS paper_graph_node (
    id UUID PRIMARY KEY,
    graph_id UUID NOT NULL REFERENCES paper_graph(id) ON DELETE CASCADE,
    entity_type VARCHAR(32) NOT NULL CHECK (
        entity_type IN ('Paper', 'Method', 'Task', 'Dataset', 'MetricResult', 'Baseline', 'Limitation')
    ),
    name VARCHAR(500) NOT NULL,
    normalized_name VARCHAR(500) NOT NULL,
    properties_json JSONB NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE (graph_id, entity_type, normalized_name)
);

CREATE INDEX IF NOT EXISTS idx_paper_graph_node_graph_type
    ON paper_graph_node (graph_id, entity_type, name);

CREATE TABLE IF NOT EXISTS paper_graph_edge (
    id UUID PRIMARY KEY,
    graph_id UUID NOT NULL REFERENCES paper_graph(id) ON DELETE CASCADE,
    source_node_id UUID NOT NULL REFERENCES paper_graph_node(id),
    target_node_id UUID NOT NULL REFERENCES paper_graph_node(id),
    relation_type VARCHAR(32) NOT NULL CHECK (
        relation_type IN ('PROPOSES', 'SOLVES', 'USES', 'ACHIEVES', 'OUTPERFORMS', 'HAS_LIMITATION')
    ),
    document_id UUID NOT NULL REFERENCES paper_graph_document(id) ON DELETE CASCADE,
    chunk_id UUID NOT NULL REFERENCES paper_graph_chunk(id) ON DELETE CASCADE,
    page_number INTEGER NOT NULL CHECK (page_number > 0),
    section_name VARCHAR(255) NOT NULL,
    evidence_quote TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_paper_graph_edge_graph
    ON paper_graph_edge (graph_id, source_node_id, target_node_id);

CREATE INDEX IF NOT EXISTS idx_paper_graph_edge_document
    ON paper_graph_edge (document_id, chunk_id);
