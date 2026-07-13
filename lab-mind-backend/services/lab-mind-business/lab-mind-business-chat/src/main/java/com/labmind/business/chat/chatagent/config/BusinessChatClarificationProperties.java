package com.labmind.business.chat.chatagent.config;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "lab-mind.chat.clarification")
public class BusinessChatClarificationProperties {

    private boolean enabled = true;

    @Min(1)
    private int maxOptions = 3;

    @DecimalMin("0.0")
    private double minTopScore = 1.0D;

    @DecimalMin("0.0")
    private double ambiguousScoreGap = 0.8D;
}
