package com.superagent.business.chat.knowledge.route.messaging;

/**
 * 当前文档问答的影子路由请求消息。
 */
public record KnowledgeShadowRouteRequestedMessage(
        String traceId,
        String workspaceId,
        String conversationId,
        Long exchangeId,
        String question,
        String rewrittenQuestion,
        String intentType,
        Long userSelectedDocumentId) {
}
