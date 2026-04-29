package com.superagent.business.chat.chatagent.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "super-agent.chat.runtime")
public class BusinessChatRuntimeProperties {

    @Min(1)
    private int maxModelCallsPerRun = 8;

    @Min(1)
    private int maxModelCallsPerThread = 40;

}
