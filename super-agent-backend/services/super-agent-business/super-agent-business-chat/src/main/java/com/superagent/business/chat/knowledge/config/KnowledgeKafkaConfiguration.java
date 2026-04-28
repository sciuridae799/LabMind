package com.superagent.business.chat.knowledge.config;

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
}
