package com.labmind.business.chat.knowledge.document.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labmind.business.chat.knowledge.document.service.KnowledgeManageService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 知识文档解析任务消费者。
 *
 * <p>这是 Kafka 异步解析链路的入口，只负责把消息恢复成 documentId/taskId，
 * 然后交给 {@link KnowledgeManageService} 执行业务处理。</p>
 *
 * <p>消费者本身不解析文件、不写数据库、不写图谱。这样做的原因是解析任务需要完整事务语义和状态校验，
 * 这些都集中在 KnowledgeManageService 中，避免消息层和业务层各自维护一套任务状态。</p>
 */
@Component
@RequiredArgsConstructor
public class KnowledgeDocumentParseConsumer {

    private final ObjectMapper objectMapper;

    private final KnowledgeManageService knowledgeManageService;

    /**
     * 消费解析消息。
     *
     * <p>Kafka 可能重投消息，因此这里不做“已消费”判断；
     * 幂等和过期任务判断由 processDocumentParseTask 根据 task 状态和 lastParseTaskId 完成。</p>
     */
    @KafkaListener(
            topics = "${lab-mind.kafka.topics.document-parse-requested}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void consume(String payload) {
        KnowledgeDocumentParseRequestedMessage message = readMessage(payload);
        knowledgeManageService.processDocumentParseTask(message.documentId(), message.taskId());
    }

    private KnowledgeDocumentParseRequestedMessage readMessage(String payload) {
        try {
            return objectMapper.readValue(payload, KnowledgeDocumentParseRequestedMessage.class);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("failed to parse document parse message", error);
        }
    }
}
