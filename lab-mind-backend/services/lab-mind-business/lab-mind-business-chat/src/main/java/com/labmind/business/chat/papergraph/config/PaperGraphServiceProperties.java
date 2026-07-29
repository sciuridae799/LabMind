package com.labmind.business.chat.papergraph.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "lab-mind.paper-graph.service")
public class PaperGraphServiceProperties {

    private String baseUrl;

    private String internalToken;
}
