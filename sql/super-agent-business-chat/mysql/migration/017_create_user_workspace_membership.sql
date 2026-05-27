-- 用户可访问工作组关系。
CREATE TABLE IF NOT EXISTS super_agent_user_workspace (
    id BIGINT NOT NULL COMMENT '主键id',
    user_id BIGINT NOT NULL COMMENT '用户id',
    workspace_id VARCHAR(64) NOT NULL COMMENT '工作组id',
    create_time DATETIME DEFAULT NULL COMMENT '创建时间',
    edit_time DATETIME DEFAULT NULL COMMENT '编辑时间',
    status TINYINT(1) DEFAULT '1' COMMENT '1:正常 0:删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_workspace (user_id, workspace_id),
    KEY idx_user_workspace_workspace (workspace_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户工作组关系表';

INSERT IGNORE INTO super_agent_user_workspace (
    id,
    user_id,
    workspace_id,
    create_time,
    edit_time,
    status
)
SELECT
    id,
    id,
    workspace_id,
    NOW(),
    NOW(),
    1
FROM super_agent_user_account
WHERE status = 1;
