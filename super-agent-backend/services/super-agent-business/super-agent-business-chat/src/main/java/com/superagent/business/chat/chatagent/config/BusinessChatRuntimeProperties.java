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

    @Min(1)
    private int maxTavilyToolCallsPerRun = 6;

    @Min(1)
    private int maxTavilyToolCallsPerThread = 30;

    @Min(1)
    private int maxParallelTools = 4;

    @Min(0)
    private int tavilyMaxRetries = 2;

    @Min(1)
    private long tavilyRetryInitialDelayMs = 200L;

    @Min(1)
    private long tavilyRetryMaxDelayMs = 1200L;

}
