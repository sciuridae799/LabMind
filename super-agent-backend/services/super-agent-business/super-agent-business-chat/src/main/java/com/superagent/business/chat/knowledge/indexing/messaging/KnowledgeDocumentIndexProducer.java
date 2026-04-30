package com.superagent.business.chat.knowledge.indexing.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superagent.business.chat.knowledge.config.KnowledgeKafkaTopicProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KnowledgeDocumentIndexProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    private final KnowledgeKafkaTopicProperties topicProperties;

    private final ObjectMapper objectMapper;

    public void publish(long documentId, long taskId, long planId) {
        KnowledgeDocumentIndexRequestedMessage message = new KnowledgeDocumentIndexRequestedMessage(
                String.valueOf(documentId),
                String.valueOf(taskId),
                String.valueOf(planId));
        try {
            kafkaTemplate.send(
                    topicProperties.getDocumentIndexRequested(),
                    message.documentId(),
                    objectMapper.writeValueAsString(message)).join();
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("failed to serialize document index message", error);
        }
    }
}
