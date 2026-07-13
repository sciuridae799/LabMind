package com.labmind.business.chat.chatagent.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "lab-mind.chat.rewrite")
public class BusinessChatRewriteProperties {

    private boolean correctionRetryEnabled = true;
}
