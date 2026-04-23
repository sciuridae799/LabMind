-- 新增模型 API 配置表
CREATE TABLE IF NOT EXISTS super_agent_model_api_config (
    id BIGINT NOT NULL COMMENT '主键id',
    provider VARCHAR(32) NOT NULL COMMENT '模型供应商编码',
    display_name VARCHAR(64) NOT NULL COMMENT '前端展示名称',
    base_url VARCHAR(255) NOT NULL COMMENT 'OpenAI兼容接口Base URL',
    model_name VARCHAR(128) NOT NULL COMMENT '模型名称',
    api_key_cipher VARCHAR(1024) DEFAULT NULL COMMENT 'API Key存储值',
    enabled TINYINT(1) NOT NULL DEFAULT '1' COMMENT '1:启用 0:禁用',
    create_time DATETIME DEFAULT NULL COMMENT '创建时间',
    edit_time DATETIME DEFAULT NULL COMMENT '编辑时间',
    status TINYINT(1) DEFAULT '1' COMMENT '1:正常 0:删除',
    PRIMARY KEY (id),
    KEY idx_super_agent_model_api_config_status_enabled (status, enabled),
    KEY idx_super_agent_model_api_config_edit_time (edit_time)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='模型API配置表';
