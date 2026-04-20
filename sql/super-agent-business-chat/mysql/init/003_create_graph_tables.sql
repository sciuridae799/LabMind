-- super-agent-business-chat Graph 持久化建表脚本
CREATE TABLE IF NOT EXISTS GRAPH_THREAD (
    thread_id VARCHAR(36) NOT NULL COMMENT 'Graph 内部线程主键',
    thread_name VARCHAR(255) NOT NULL COMMENT '业务线程名，通常就是 conversationId',
    is_released BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否已经被释放',
    PRIMARY KEY (thread_id),
    UNIQUE KEY IDX_GRAPH_THREAD_NAME_RELEASED (thread_name, is_released)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Spring AI Alibaba Graph 线程表';

CREATE TABLE IF NOT EXISTS GRAPH_CHECKPOINT (
    checkpoint_id VARCHAR(36) NOT NULL COMMENT 'Checkpoint ID',
    thread_id VARCHAR(36) NOT NULL COMMENT '关联线程ID',
    node_id VARCHAR(255) NULL COMMENT '当前节点ID',
    next_node_id VARCHAR(255) NULL COMMENT '下一个节点ID',
    state_data JSON NOT NULL COMMENT '序列化后的 Agent 状态',
    saved_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '保存时间',
    PRIMARY KEY (checkpoint_id),
    KEY idx_graph_checkpoint_thread_saved (thread_id, saved_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Spring AI Alibaba Graph checkpoint 表';
