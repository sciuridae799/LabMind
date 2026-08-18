UPDATE lab_mind_chat_exchange_trace_stage
SET duration_ms = 0
WHERE duration_ms < 0;

UPDATE lab_mind_chat_model_call_trace
SET duration_ms = 0
WHERE duration_ms < 0;

UPDATE lab_mind_chat_tool_call_trace
SET duration_ms = 0
WHERE duration_ms < 0;

ALTER TABLE lab_mind_chat_exchange_trace_stage
    MODIFY COLUMN start_time DATETIME(3) DEFAULT NULL COMMENT '阶段开始时间',
    MODIFY COLUMN end_time DATETIME(3) DEFAULT NULL COMMENT '阶段结束时间';

ALTER TABLE lab_mind_chat_model_call_trace
    MODIFY COLUMN start_time DATETIME(3) DEFAULT NULL COMMENT '开始时间',
    MODIFY COLUMN end_time DATETIME(3) DEFAULT NULL COMMENT '结束时间';

ALTER TABLE lab_mind_chat_tool_call_trace
    MODIFY COLUMN start_time DATETIME(3) DEFAULT NULL COMMENT '开始时间',
    MODIFY COLUMN end_time DATETIME(3) DEFAULT NULL COMMENT '结束时间';
