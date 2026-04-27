package com.superagent.business.chat.knowledge.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        Neo4jKnowledgeGraphProperties.class,
        KnowledgeDocumentStorageProperties.class
})
public class Neo4jKnowledgeGraphConfiguration {
}
