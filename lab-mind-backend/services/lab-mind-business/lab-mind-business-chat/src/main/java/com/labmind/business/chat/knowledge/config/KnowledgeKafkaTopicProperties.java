package com.labmind.business.chat.knowledge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "lab-mind.kafka.topics")
public class KnowledgeKafkaTopicProperties {

    private String documentParseRequested;

    private String documentIndexRequested;

    private String shadowRouteRequested;
}
