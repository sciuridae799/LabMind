-- 认证与工作组边界迁移。
CREATE TABLE IF NOT EXISTS super_agent_workspace (
    id BIGINT NOT NULL COMMENT '主键id',
    workspace_id VARCHAR(64) NOT NULL COMMENT '工作组id',
    workspace_name VARCHAR(128) NOT NULL COMMENT '工作组名称',
    create_time DATETIME DEFAULT NULL COMMENT '创建时间',
    edit_time DATETIME DEFAULT NULL COMMENT '编辑时间',
    status TINYINT(1) DEFAULT '1' COMMENT '1:正常 0:删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_workspace_id (workspace_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作组表';

CREATE TABLE IF NOT EXISTS super_agent_user_account (
    id BIGINT NOT NULL COMMENT '主键id',
    account VARCHAR(64) NOT NULL COMMENT '登录账号',
    display_name VARCHAR(128) NOT NULL COMMENT '展示名称',
    password_hash VARCHAR(128) NOT NULL COMMENT '密码哈希',
    password_salt VARCHAR(64) NOT NULL COMMENT '密码盐',
    role VARCHAR(32) NOT NULL COMMENT '角色 guest/user/super_admin',
    workspace_id VARCHAR(64) NOT NULL COMMENT '所属工作组id',
    enabled TINYINT(1) NOT NULL COMMENT '1:启用 0:停用',
    create_time DATETIME DEFAULT NULL COMMENT '创建时间',
    edit_time DATETIME DEFAULT NULL COMMENT '编辑时间',
    status TINYINT(1) DEFAULT '1' COMMENT '1:正常 0:删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_account (account),
    KEY idx_user_workspace (workspace_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户账号表';

CREATE TABLE IF NOT EXISTS super_agent_auth_session (
    id BIGINT NOT NULL COMMENT '主键id',
    token VARCHAR(128) NOT NULL COMMENT '登录令牌',
    user_id BIGINT DEFAULT NULL COMMENT '用户id，访客为空',
    account VARCHAR(64) NOT NULL COMMENT '账号快照',
    display_name VARCHAR(128) NOT NULL COMMENT '展示名称快照',
    role VARCHAR(32) NOT NULL COMMENT '角色快照',
    workspace_id VARCHAR(64) NOT NULL COMMENT '工作组id快照',
    expire_time DATETIME NOT NULL COMMENT '过期时间',
    create_time DATETIME DEFAULT NULL COMMENT '创建时间',
    edit_time DATETIME DEFAULT NULL COMMENT '编辑时间',
    status TINYINT(1) DEFAULT '1' COMMENT '1:正常 0:删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_auth_token (token),
    KEY idx_auth_session_workspace (workspace_id),
    KEY idx_auth_session_expire (expire_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='登录会话表';

INSERT IGNORE INTO super_agent_workspace (
    id, workspace_id, workspace_name, create_time, edit_time, status
) VALUES (
    202601010000000001, 'public-demo', '访客体验资料库', NOW(), NOW(), 1
);

INSERT IGNORE INTO super_agent_workspace (
    id, workspace_id, workspace_name, create_time, edit_time, status
) VALUES (
    202601010000000002, 'lab-default', '默认实验室', NOW(), NOW(), 1
);

ALTER TABLE super_agent_document ADD COLUMN workspace_id VARCHAR(64) NULL COMMENT '所属工作组id' AFTER document_name;
ALTER TABLE super_agent_document_chunk ADD COLUMN workspace_id VARCHAR(64) NULL COMMENT '所属工作组id' AFTER document_id;
ALTER TABLE super_agent_document_parent_block ADD COLUMN workspace_id VARCHAR(64) NULL COMMENT '所属工作组id' AFTER document_id;
ALTER TABLE super_agent_knowledge_document_profile ADD COLUMN workspace_id VARCHAR(64) NULL COMMENT '所属工作组id' AFTER document_id;
ALTER TABLE super_agent_knowledge_route_trace ADD COLUMN workspace_id VARCHAR(64) NULL COMMENT '所属工作组id' AFTER trace_id;
ALTER TABLE super_agent_chat_dialogue ADD COLUMN workspace_id VARCHAR(64) NULL COMMENT '所属工作组id' AFTER dialogue_code;
ALTER TABLE super_agent_chat_exchange ADD COLUMN workspace_id VARCHAR(64) NULL COMMENT '所属工作组id' AFTER dialogue_code;
ALTER TABLE super_agent_chat_memory_summary ADD COLUMN workspace_id VARCHAR(64) NULL COMMENT '所属工作组id' AFTER dialogue_code;
ALTER TABLE super_agent_chat_exchange_trace_stage ADD COLUMN workspace_id VARCHAR(64) NULL COMMENT '所属工作组id' AFTER dialogue_code;
ALTER TABLE super_agent_chat_session_state ADD COLUMN workspace_id VARCHAR(64) NULL COMMENT '所属工作组id' AFTER state_key;

UPDATE super_agent_document SET workspace_id = 'lab-default';
UPDATE super_agent_document_chunk SET workspace_id = 'lab-default';
UPDATE super_agent_document_parent_block SET workspace_id = 'lab-default';
UPDATE super_agent_knowledge_document_profile SET workspace_id = 'lab-default';
UPDATE super_agent_knowledge_route_trace SET workspace_id = 'lab-default';
UPDATE super_agent_chat_dialogue SET workspace_id = 'lab-default';
UPDATE super_agent_chat_exchange SET workspace_id = 'lab-default';
UPDATE super_agent_chat_memory_summary SET workspace_id = 'lab-default';
UPDATE super_agent_chat_exchange_trace_stage SET workspace_id = 'lab-default';
UPDATE super_agent_chat_session_state SET workspace_id = 'lab-default';

ALTER TABLE super_agent_document MODIFY COLUMN workspace_id VARCHAR(64) NOT NULL COMMENT '所属工作组id';
ALTER TABLE super_agent_document_chunk MODIFY COLUMN workspace_id VARCHAR(64) NOT NULL COMMENT '所属工作组id';
ALTER TABLE super_agent_document_parent_block MODIFY COLUMN workspace_id VARCHAR(64) NOT NULL COMMENT '所属工作组id';
ALTER TABLE super_agent_knowledge_document_profile MODIFY COLUMN workspace_id VARCHAR(64) NOT NULL COMMENT '所属工作组id';
ALTER TABLE super_agent_knowledge_route_trace MODIFY COLUMN workspace_id VARCHAR(64) NOT NULL COMMENT '所属工作组id';
ALTER TABLE super_agent_chat_dialogue MODIFY COLUMN workspace_id VARCHAR(64) NOT NULL COMMENT '所属工作组id';
ALTER TABLE super_agent_chat_exchange MODIFY COLUMN workspace_id VARCHAR(64) NOT NULL COMMENT '所属工作组id';
ALTER TABLE super_agent_chat_memory_summary MODIFY COLUMN workspace_id VARCHAR(64) NOT NULL COMMENT '所属工作组id';
ALTER TABLE super_agent_chat_exchange_trace_stage MODIFY COLUMN workspace_id VARCHAR(64) NOT NULL COMMENT '所属工作组id';
ALTER TABLE super_agent_chat_session_state MODIFY COLUMN workspace_id VARCHAR(64) NOT NULL COMMENT '所属工作组id';

ALTER TABLE super_agent_chat_session_state DROP INDEX uk_super_agent_chat_session_state_key;
ALTER TABLE super_agent_chat_session_state ADD UNIQUE KEY uk_super_agent_chat_session_state_workspace_key (workspace_id, state_key);

CREATE INDEX idx_document_workspace ON super_agent_document (workspace_id, status, create_time);
CREATE INDEX idx_chunk_workspace ON super_agent_document_chunk (workspace_id, document_id);
CREATE INDEX idx_parent_workspace ON super_agent_document_parent_block (workspace_id, document_id);
CREATE INDEX idx_profile_workspace ON super_agent_knowledge_document_profile (workspace_id, document_id, status);
CREATE INDEX idx_route_trace_workspace ON super_agent_knowledge_route_trace (workspace_id, status, create_time);
CREATE INDEX idx_dialogue_workspace ON super_agent_chat_dialogue (workspace_id, dialogue_code, status);
CREATE INDEX idx_exchange_workspace ON super_agent_chat_exchange (workspace_id, dialogue_code, status);
CREATE INDEX idx_memory_workspace ON super_agent_chat_memory_summary (workspace_id, dialogue_code, status);
CREATE INDEX idx_trace_stage_workspace ON super_agent_chat_exchange_trace_stage (workspace_id, dialogue_code, exchange_id, status);
CREATE INDEX idx_session_state_workspace ON super_agent_chat_session_state (workspace_id, state_key, status);
