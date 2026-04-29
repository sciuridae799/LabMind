package com.superagent.business.chat.chatagent.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "super-agent.chat.history-summary")
public class BusinessChatHistorySummaryProperties {

    @NotNull
    private Boolean enabled;

    @NotNull
    @Min(0)
    private Integer keepRecentTurns;

    @NotNull
    @Min(1)
    private Integer compressionBatchTurns;

    @NotNull
    @Min(1)
    private Integer recentTranscriptMaxChars;

    @NotNull
    @Min(1)
    private Integer summaryMaxChars;
}
