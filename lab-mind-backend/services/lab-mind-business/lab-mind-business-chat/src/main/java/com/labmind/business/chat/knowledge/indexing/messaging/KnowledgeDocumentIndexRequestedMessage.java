package com.labmind.business.chat.knowledge.indexing.messaging;

/**
 * 知识文档索引构建请求消息。
 *
 * <p>只携带 documentId/taskId/planId，消费端必须回表校验任务和策略方案仍然是文档当前状态。</p>
 */
public record KnowledgeDocumentIndexRequestedMessage(
        String documentId,
        String taskId,
        String planId) {
}
