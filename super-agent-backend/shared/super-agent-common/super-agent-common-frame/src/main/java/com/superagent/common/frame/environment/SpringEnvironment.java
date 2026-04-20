package com.superagent.common.frame.environment;

import java.util.Map;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

public class SpringEnvironment implements EnvironmentPostProcessor, Ordered {

    static final String ALLOW_BEAN_DEFINITION_OVERRIDING = "spring.main.allow-bean-definition-overriding";

    private static final String PROPERTY_SOURCE_NAME = "superAgentCommonFrameEnvironment";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (StringUtils.hasText(environment.getProperty(ALLOW_BEAN_DEFINITION_OVERRIDING))) {
            return;
        }
        environment.getPropertySources().addLast(new MapPropertySource(
                PROPERTY_SOURCE_NAME,
                Map.of(ALLOW_BEAN_DEFINITION_OVERRIDING, Boolean.TRUE.toString())));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}
