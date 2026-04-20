-- super-agent-business-chat 知识域建表脚本
CREATE TABLE IF NOT EXISTS super_agent_knowledge_scope_node (
    id BIGINT NOT NULL COMMENT '主键id',
    scope_code VARCHAR(64) NOT NULL COMMENT '知识范围编码',
    scope_name VARCHAR(128) NOT NULL COMMENT '知识范围名称',
    parent_scope_code VARCHAR(64) DEFAULT NULL COMMENT '父级知识范围编码',
    description VARCHAR(1024) DEFAULT NULL COMMENT '范围描述',
    aliases VARCHAR(512) DEFAULT NULL COMMENT '别名，英文逗号分隔',
    examples TEXT COMMENT '典型问题 JSON 数组',
    sort_order INT DEFAULT '0' COMMENT '排序值',
    create_time DATETIME DEFAULT NULL COMMENT '创建时间',
    edit_time DATETIME DEFAULT NULL COMMENT '编辑时间',
    status TINYINT(1) DEFAULT '1' COMMENT '1:正常 0:删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_scope_code (scope_code),
    KEY idx_parent_scope_code (parent_scope_code),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识范围节点表';

CREATE TABLE IF NOT EXISTS super_agent_knowledge_topic_node (
    id BIGINT NOT NULL COMMENT '主键id',
    topic_code VARCHAR(64) NOT NULL COMMENT '主题编码',
    topic_name VARCHAR(128) NOT NULL COMMENT '主题名称',
    scope_code VARCHAR(64) NOT NULL COMMENT '所属知识范围编码',
    description VARCHAR(1024) DEFAULT NULL COMMENT '主题描述',
    aliases VARCHAR(512) DEFAULT NULL COMMENT '别名，英文逗号分隔',
    examples TEXT COMMENT '典型问题 JSON 数组',
    answer_shape VARCHAR(64) DEFAULT NULL COMMENT '建议回答形态 explain/list/steps/compare/structure',
    execution_preference VARCHAR(64) DEFAULT NULL COMMENT '执行偏好 retrieval/graph_only/graph_then_evidence/graph_assist',
    sort_order INT DEFAULT '0' COMMENT '排序值',
    create_time DATETIME DEFAULT NULL COMMENT '创建时间',
    edit_time DATETIME DEFAULT NULL COMMENT '编辑时间',
    status TINYINT(1) DEFAULT '1' COMMENT '1:正常 0:删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_topic_code (topic_code),
    KEY idx_scope_code (scope_code),
    KEY idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识主题节点表';
