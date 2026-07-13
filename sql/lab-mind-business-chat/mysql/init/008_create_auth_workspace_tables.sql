-- lab-mind-business-chat 认证与工作组边界表
CREATE TABLE IF NOT EXISTS lab_mind_workspace (
    id BIGINT NOT NULL COMMENT '主键id',
    workspace_id VARCHAR(64) NOT NULL COMMENT '工作组id',
    workspace_name VARCHAR(128) NOT NULL COMMENT '工作组名称',
    create_time DATETIME DEFAULT NULL COMMENT '创建时间',
    edit_time DATETIME DEFAULT NULL COMMENT '编辑时间',
    status TINYINT(1) DEFAULT '1' COMMENT '1:正常 0:删除',
    PRIMARY KEY (id),
    UNIQUE KEY uk_workspace_id (workspace_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作组表';

CREATE TABLE IF NOT EXISTS lab_mind_user_account (
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

CREATE TABLE IF NOT EXISTS lab_mind_user_workspace (
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

CREATE TABLE IF NOT EXISTS lab_mind_auth_session (
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

INSERT IGNORE INTO lab_mind_workspace (
    id, workspace_id, workspace_name, create_time, edit_time, status
) VALUES (
    202601010000000001, 'public-demo', '访客体验资料库', NOW(), NOW(), 1
);
