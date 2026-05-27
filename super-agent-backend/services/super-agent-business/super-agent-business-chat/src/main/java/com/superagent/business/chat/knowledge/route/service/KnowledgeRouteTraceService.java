package com.superagent.business.chat.knowledge.route.service;

import com.superagent.business.chat.knowledge.route.model.KnowledgeRouteDecision;

/**
 * 知识路由追踪写入边界。
 *
 * <p>自动路由和影子路由都通过这里落库，回答链路不直接拼装 trace 表字段。</p>
 */
public interface KnowledgeRouteTraceService {

    /**
     * 记录一次完整路由结果。
     *
     * <p>当 routeMode 为 SHADOW 时，userSelectedDocumentId 是用户手动选择的文档，用于计算命中率；
     * 当 routeMode 为 AUTO 时，该字段为空，只记录自动路由决策。</p>
     */
    void recordRouteTrace(
            String traceId,
            String workspaceId,
            String conversationId,
            Long exchangeId,
            String question,
            String rewrittenQuestion,
            String intentType,
            String routeMode,
            Long userSelectedDocumentId,
            KnowledgeRouteDecision routeDecision);
}
