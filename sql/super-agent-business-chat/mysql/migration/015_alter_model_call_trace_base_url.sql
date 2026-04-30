ALTER TABLE super_agent_chat_model_call_trace
    ADD COLUMN base_url VARCHAR(255) DEFAULT NULL COMMENT 'OpenAI兼容接口Base URL' AFTER provider;
