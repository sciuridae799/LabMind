-- 记录聊天页刷新时应恢复的活动会话。
CREATE TABLE IF NOT EXISTS super_agent_chat_session_state (
    id BIGINT NOT NULL COMMENT '主键id',
    state_key VARCHAR(64) NOT NULL COMMENT '状态键，当前为全局聊天页状态',
    active_conversation_id VARCHAR(64) DEFAULT NULL COMMENT '聊天页刷新时应恢复的活动会话编号',
    create_time DATETIME DEFAULT NULL COMMENT '创建时间',
    edit_time DATETIME DEFAULT NULL COMMENT '编辑时间',
    status TINYINT(1) DEFAULT '1' COMMENT '1:正常 0:删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_super_agent_chat_session_state_key (state_key),
    KEY idx_super_agent_chat_session_state_conversation (active_conversation_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='业务聊天页活动会话状态表';
