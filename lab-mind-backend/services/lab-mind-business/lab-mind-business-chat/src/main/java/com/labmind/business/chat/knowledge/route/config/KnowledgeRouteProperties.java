package com.labmind.business.chat.knowledge.route.config;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "lab-mind.knowledge.route")
public class KnowledgeRouteProperties {

    @Min(1)
    private int scopeTopK = 3;

    @Min(1)
    private int topicTopK = 5;

    @DecimalMin("0.0")
    private double topScopeTopicBoost = 8.0D;

    @DecimalMin("0.0")
    private double topScopeDocumentBoost = 15.0D;

    @DecimalMin("0.0")
    private double topTopicDocumentBoost = 10.0D;

    @DecimalMin("0.0")
    private double lexicalWeight = 1.0D;

    @DecimalMin("0.0")
    private double semanticWeight = 1.6D;

    @DecimalMin("0.0")
    private double successConfidence = 0.55D;
}
