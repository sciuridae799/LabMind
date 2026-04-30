package com.superagent.business.chat.knowledge.route.config;

import com.superagent.business.chat.knowledge.document.config.KnowledgeDocumentStorageProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        Neo4jKnowledgeGraphProperties.class,
        KnowledgeDocumentStorageProperties.class,
        KnowledgeRouteProperties.class
})
public class Neo4jKnowledgeGraphConfiguration {
}
