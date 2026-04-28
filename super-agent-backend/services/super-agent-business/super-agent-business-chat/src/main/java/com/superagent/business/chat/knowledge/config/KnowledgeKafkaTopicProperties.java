package com.superagent.business.chat.knowledge.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "super-agent.kafka.topics")
public class KnowledgeKafkaTopicProperties {

    private String documentParseRequested;
}
