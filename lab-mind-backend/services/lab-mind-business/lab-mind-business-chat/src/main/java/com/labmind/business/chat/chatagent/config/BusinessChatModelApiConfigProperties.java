package com.labmind.business.chat.chatagent.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "lab-mind.chat.model-api-config")
public class BusinessChatModelApiConfigProperties {

    private String apiKeyAesKeyBase64;
}
