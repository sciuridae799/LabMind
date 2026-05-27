ALTER TABLE public.super_agent_document_embedding
    ADD COLUMN workspace_id VARCHAR(64);

COMMENT ON COLUMN public.super_agent_document_embedding.workspace_id IS '所属工作组id';

UPDATE public.super_agent_document_embedding
SET workspace_id = 'lab-default';

ALTER TABLE public.super_agent_document_embedding
    ALTER COLUMN workspace_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_super_agent_document_embedding_workspace
    ON public.super_agent_document_embedding (workspace_id, document_id, status);
