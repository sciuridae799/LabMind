package com.labmind.business.chat.knowledge.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;

@EnableKafka
@Configuration
@EnableConfigurationProperties(KnowledgeKafkaTopicProperties.class)
public class KnowledgeKafkaConfiguration {

    @Bean
    public NewTopic documentParseRequestedTopic(KnowledgeKafkaTopicProperties topicProperties) {
        return new NewTopic(topicProperties.getDocumentParseRequested(), 1, (short) 1);
    }

    @Bean
    public NewTopic documentIndexRequestedTopic(KnowledgeKafkaTopicProperties topicProperties) {
        return new NewTopic(topicProperties.getDocumentIndexRequested(), 1, (short) 1);
    }

    @Bean
    public NewTopic shadowRouteRequestedTopic(KnowledgeKafkaTopicProperties topicProperties) {
        return new NewTopic(topicProperties.getShadowRouteRequested(), 1, (short) 1);
    }
}
