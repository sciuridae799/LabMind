ALTER TABLE super_agent_model_api_config
    ADD COLUMN sort_order INT NOT NULL DEFAULT '1000' COMMENT '排序值，越小越靠前' AFTER enabled;

CREATE INDEX idx_super_agent_model_api_config_sort_order
    ON super_agent_model_api_config (sort_order);

SET @row_number := 0;

UPDATE super_agent_model_api_config
SET sort_order = (@row_number := @row_number + 1) * 1000
WHERE status = 1
ORDER BY edit_time DESC, id DESC;
