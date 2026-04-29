package com.superagent.business.chat.chatagent.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        BusinessChatModelApiConfigProperties.class,
        BusinessChatHistorySummaryProperties.class,
        BusinessChatRewriteProperties.class,
        BusinessChatClarificationProperties.class,
        BusinessChatRuntimeProperties.class,
        BusinessChatRecommendationProperties.class
})
public class BusinessChatModelApiConfigConfiguration {
}
