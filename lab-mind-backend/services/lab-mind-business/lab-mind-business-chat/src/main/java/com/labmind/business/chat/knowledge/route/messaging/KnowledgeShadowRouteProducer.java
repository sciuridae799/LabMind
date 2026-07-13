package com.labmind.business.chat.knowledge.route.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labmind.business.chat.knowledge.config.KnowledgeKafkaTopicProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 影子路由任务生产者。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeShadowRouteProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;

    private final KnowledgeKafkaTopicProperties topicProperties;

    private final ObjectMapper objectMapper;

    public void publish(KnowledgeShadowRouteRequestedMessage message) {
        String payload = writeMessage(message);
        kafkaTemplate.send(topicProperties.getShadowRouteRequested(), message.traceId(), payload)
                .whenComplete((result, error) -> {
                    if (error != null) {
                        log.error("failed to publish shadow route message, traceId={}", message.traceId(), error);
                    }
                });
    }

    private String writeMessage(KnowledgeShadowRouteRequestedMessage message) {
        try {
            return objectMapper.writeValueAsString(message);
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("failed to serialize shadow route message", error);
        }
    }
}
