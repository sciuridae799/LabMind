package com.superagent.business.chat.knowledge.indexing.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superagent.business.chat.knowledge.document.service.KnowledgeManageService;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KnowledgeDocumentIndexConsumer {

    private final ObjectMapper objectMapper;

    private final KnowledgeManageService knowledgeManageService;

    @KafkaListener(
            topics = "${super-agent.kafka.topics.document-index-requested}",
            groupId = "${spring.kafka.consumer.group-id}")
    public void consume(String payload) {
        KnowledgeDocumentIndexRequestedMessage message = readMessage(payload);
        knowledgeManageService.processDocumentIndexTask(message.documentId(), message.taskId(), message.planId());
    }

    private KnowledgeDocumentIndexRequestedMessage readMessage(String payload) {
        try {
            return objectMapper.readValue(payload, KnowledgeDocumentIndexRequestedMessage.class);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("failed to parse document index message", error);
        }
    }
}
