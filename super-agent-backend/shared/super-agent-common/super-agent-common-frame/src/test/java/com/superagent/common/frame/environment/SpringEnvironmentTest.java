package com.superagent.common.frame.environment;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class SpringEnvironmentTest {

    @Test
    void shouldEnableBeanDefinitionOverridingWhenPropertyIsAbsent() {
        MockEnvironment environment = new MockEnvironment();

        new SpringEnvironment().postProcessEnvironment(environment, new SpringApplication(Object.class));

        assertThat(environment.getProperty(SpringEnvironment.ALLOW_BEAN_DEFINITION_OVERRIDING)).isEqualTo("true");
    }

    @Test
    void shouldRespectExplicitBeanDefinitionOverridingConfiguration() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty(SpringEnvironment.ALLOW_BEAN_DEFINITION_OVERRIDING, "false");

        new SpringEnvironment().postProcessEnvironment(environment, new SpringApplication(Object.class));

        assertThat(environment.getProperty(SpringEnvironment.ALLOW_BEAN_DEFINITION_OVERRIDING)).isEqualTo("false");
    }

    @Test
    void shouldRegisterEnvironmentPostProcessorInSpringFactories() throws Exception {
        try (var inputStream = getClass().getClassLoader().getResourceAsStream("META-INF/spring.factories")) {
            assertThat(inputStream).isNotNull();
            String factories = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(factories).contains("com.superagent.common.frame.environment.SpringEnvironment");
        }
    }
}
