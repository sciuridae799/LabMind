ALTER TABLE lab_mind_chat_dialogue
    ADD COLUMN auth_session_token VARCHAR(128) NOT NULL DEFAULT '' COMMENT '访客登录会话token，用于隔离访客历史' AFTER workspace_id,
    ADD KEY idx_dialogue_guest_session (workspace_id, auth_session_token, status);

ALTER TABLE lab_mind_chat_session_state
    DROP INDEX uk_lab_mind_chat_session_state_workspace_key,
    ADD COLUMN auth_session_token VARCHAR(128) NOT NULL DEFAULT '' COMMENT '访客登录会话token，用于隔离访客当前会话' AFTER workspace_id,
    ADD UNIQUE KEY uk_chat_state_workspace_scope_key (workspace_id, auth_session_token, state_key),
    ADD KEY idx_chat_state_guest_session (workspace_id, auth_session_token, status);
