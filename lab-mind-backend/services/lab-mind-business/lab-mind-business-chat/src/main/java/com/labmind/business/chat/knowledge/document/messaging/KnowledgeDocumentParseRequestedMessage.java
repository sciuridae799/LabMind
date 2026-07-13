package com.labmind.business.chat.knowledge.document.messaging;

/**
 * 知识文档解析请求消息。
 *
 * <p>只携带 documentId 和 taskId，消费者必须回表校验任务是否仍为文档当前解析任务。</p>
 */
public record KnowledgeDocumentParseRequestedMessage(
        String documentId,
        String taskId) {
}
