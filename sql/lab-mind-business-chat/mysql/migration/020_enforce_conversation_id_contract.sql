UPDATE lab_mind_chat_exchange exchange_data
INNER JOIN lab_mind_chat_dialogue dialogue_data
    ON exchange_data.dialogue_code = dialogue_data.dialogue_code
SET exchange_data.dialogue_code = dialogue_data.dialogue_code;

UPDATE lab_mind_chat_memory_summary summary_data
INNER JOIN lab_mind_chat_dialogue dialogue_data
    ON summary_data.dialogue_code = dialogue_data.dialogue_code
SET summary_data.dialogue_code = dialogue_data.dialogue_code;

UPDATE lab_mind_chat_exchange_trace_stage trace_stage_data
INNER JOIN lab_mind_chat_dialogue dialogue_data
    ON trace_stage_data.dialogue_code = dialogue_data.dialogue_code
SET trace_stage_data.dialogue_code = dialogue_data.dialogue_code;

UPDATE lab_mind_chat_model_call_trace model_trace_data
INNER JOIN lab_mind_chat_dialogue dialogue_data
    ON model_trace_data.dialogue_code = dialogue_data.dialogue_code
SET model_trace_data.dialogue_code = dialogue_data.dialogue_code;

UPDATE lab_mind_chat_tool_call_trace tool_trace_data
INNER JOIN lab_mind_chat_dialogue dialogue_data
    ON tool_trace_data.dialogue_code = dialogue_data.dialogue_code
SET tool_trace_data.dialogue_code = dialogue_data.dialogue_code;

UPDATE lab_mind_chat_session_state session_state_data
INNER JOIN lab_mind_chat_dialogue dialogue_data
    ON session_state_data.active_conversation_id = dialogue_data.dialogue_code
SET session_state_data.active_conversation_id = dialogue_data.dialogue_code;

UPDATE lab_mind_knowledge_route_trace route_trace_data
INNER JOIN lab_mind_chat_dialogue dialogue_data
    ON route_trace_data.conversation_id = dialogue_data.dialogue_code
SET route_trace_data.conversation_id = dialogue_data.dialogue_code;

UPDATE GRAPH_THREAD graph_thread_data
INNER JOIN lab_mind_chat_dialogue dialogue_data
    ON graph_thread_data.thread_name = dialogue_data.dialogue_code
SET graph_thread_data.thread_name = dialogue_data.dialogue_code;

ALTER TABLE lab_mind_chat_dialogue
    MODIFY COLUMN dialogue_code VARCHAR(64) COLLATE utf8mb4_bin NOT NULL COMMENT '业务会话编号';

ALTER TABLE lab_mind_chat_exchange
    MODIFY COLUMN dialogue_code VARCHAR(64) COLLATE utf8mb4_bin NOT NULL COMMENT '所属业务会话编号';

ALTER TABLE lab_mind_chat_memory_summary
    MODIFY COLUMN dialogue_code VARCHAR(64) COLLATE utf8mb4_bin NOT NULL COMMENT '所属业务会话编号';

ALTER TABLE lab_mind_chat_exchange_trace_stage
    MODIFY COLUMN dialogue_code VARCHAR(64) COLLATE utf8mb4_bin NOT NULL COMMENT '所属业务会话编号';

ALTER TABLE lab_mind_chat_model_call_trace
    MODIFY COLUMN dialogue_code VARCHAR(64) COLLATE utf8mb4_bin NOT NULL COMMENT '所属业务会话编号';

ALTER TABLE lab_mind_chat_tool_call_trace
    MODIFY COLUMN dialogue_code VARCHAR(64) COLLATE utf8mb4_bin NOT NULL COMMENT '所属业务会话编号';

ALTER TABLE lab_mind_chat_session_state
    MODIFY COLUMN active_conversation_id VARCHAR(64) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '聊天页刷新时应恢复的活动会话编号';

ALTER TABLE lab_mind_knowledge_route_trace
    MODIFY COLUMN conversation_id VARCHAR(64) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '会话编号';

ALTER TABLE GRAPH_THREAD
    MODIFY COLUMN thread_name VARCHAR(64) COLLATE utf8mb4_bin NOT NULL COMMENT '业务线程名，即 conversationId';
