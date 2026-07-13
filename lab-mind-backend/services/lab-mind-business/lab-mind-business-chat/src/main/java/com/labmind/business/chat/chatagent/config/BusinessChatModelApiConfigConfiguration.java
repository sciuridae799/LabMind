package com.labmind.business.chat.chatagent.config;

import com.labmind.business.chat.knowledge.retrieval.config.KnowledgeRetrievalProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        BusinessChatModelApiConfigProperties.class,
        BusinessChatHistorySummaryProperties.class,
        BusinessChatRewriteProperties.class,
        BusinessChatClarificationProperties.class,
        BusinessChatRuntimeProperties.class,
        BusinessChatRecommendationProperties.class,
        KnowledgeRetrievalProperties.class
})
public class BusinessChatModelApiConfigConfiguration {
}
