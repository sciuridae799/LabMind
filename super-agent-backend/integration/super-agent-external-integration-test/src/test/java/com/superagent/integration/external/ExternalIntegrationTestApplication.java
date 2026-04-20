package com.superagent.integration.external;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

@SpringBootConfiguration
@EnableAutoConfiguration
@Import(ExternalIntegrationTestConfiguration.class)
@EnableConfigurationProperties(ExternalServiceIntegrationProperties.class)
public class ExternalIntegrationTestApplication {
}
