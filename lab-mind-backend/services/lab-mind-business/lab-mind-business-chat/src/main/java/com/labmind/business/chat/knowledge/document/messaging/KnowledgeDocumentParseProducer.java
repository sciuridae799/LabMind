package com.labmind.business.chat.knowledge.document.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labmind.business.chat.knowledge.config.KnowledgeKafkaTopicProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 知识文档解析任务生产者。
 *
 * <p>这是文档上传链路和异步解析链路之间的边界。</p>
 *
 * <p>上传接口不会在 HTTP 请求内直接解析文档，而是先把原文对象、document 记录和 task 记录落库，
 * 再通过本生产者发布 Kafka 消息。消息只携带 documentId/taskId，消费端必须回库读取任务快照，
 * 这样可以保证解析依据来自已持久化的数据，而不是请求内存对象。</p>
 *
 * <p>消息 key 使用 documentId，同一文档的任务会尽量进入同一分区，减少同一文档多任务乱序带来的覆盖风险。
 * 真正的最终保护仍在消费端校验 document.lastParseTaskId。</p>
 */
@Component
@RequiredArgsConstructor
public class KnowledgeDocumentParseProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    private final KnowledgeKafkaTopicProperties topicProperties;

    private final ObjectMapper objectMapper;

    /**
     * 发布文档解析请求。
     *
     * <p>这里使用 join 等待发送结果；如果 Kafka 写入失败，上传链路会把 document/task 标记为失败，
     * 不留下一个永远等待消费的 PENDING 任务。</p>
     */
    public void publish(long documentId, long taskId) {
        String topic = topicProperties.getDocumentParseRequested();
        KnowledgeDocumentParseRequestedMessage message = new KnowledgeDocumentParseRequestedMessage(
                String.valueOf(documentId),
                String.valueOf(taskId));
        try {
            kafkaTemplate.send(topic, message.documentId(), objectMapper.writeValueAsString(message)).join();
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("failed to serialize document parse message", error);
        }
    }
}
