ALTER TABLE lab_mind_knowledge_route_trace
    ADD COLUMN user_selected_document_id BIGINT DEFAULT NULL COMMENT '用户手动选择的文档id，影子路由使用' AFTER route_result_json,
    ADD COLUMN route_top_document_id BIGINT DEFAULT NULL COMMENT '路由第一名文档id' AFTER user_selected_document_id,
    ADD COLUMN hit_selected_document TINYINT DEFAULT NULL COMMENT '影子路由是否命中用户手动选择 1:命中 0:未命中' AFTER route_top_document_id,
    ADD COLUMN confidence DECIMAL(10, 6) NOT NULL DEFAULT '0.000000' COMMENT '路由置信度' AFTER hit_selected_document,
    ADD COLUMN route_status VARCHAR(32) NOT NULL DEFAULT 'FAILED' COMMENT '路由状态 SUCCESS/LOW_CONFIDENCE/FAILED' AFTER confidence,
    ADD COLUMN route_mode VARCHAR(32) NOT NULL DEFAULT 'AUTO' COMMENT '路由模式 AUTO/SHADOW' AFTER route_status,
    ADD KEY idx_route_status (route_status),
    ADD KEY idx_shadow_hit (route_mode, hit_selected_document);
