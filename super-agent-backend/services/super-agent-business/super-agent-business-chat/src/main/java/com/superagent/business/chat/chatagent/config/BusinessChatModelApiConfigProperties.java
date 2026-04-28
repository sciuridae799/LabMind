package com.superagent.business.chat.chatagent.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "super-agent.chat.model-api-config")
public class BusinessChatModelApiConfigProperties {

    private String apiKeyAesKeyBase64;
}
