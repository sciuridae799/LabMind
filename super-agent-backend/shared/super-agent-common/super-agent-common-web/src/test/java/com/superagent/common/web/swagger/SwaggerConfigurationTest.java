package com.superagent.common.web.swagger;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class SwaggerConfigurationTest {

    @Test
    void shouldCreateDefaultOpenApiGroup() {
        GroupedOpenApi groupedOpenApi = new SwaggerConfiguration().superAgentGroupedOpenApi();

        assertThat(groupedOpenApi.getGroup()).isEqualTo("default");
    }

    @Test
    void shouldCreateOpenApiInfoFromApplicationName() {
        MockEnvironment environment = new MockEnvironment().withProperty("spring.application.name", "super-agent-business");

        OpenAPI openAPI = new SwaggerConfiguration().superAgentOpenApi(environment);

        assertThat(openAPI.getInfo()).isNotNull();
        assertThat(openAPI.getInfo().getTitle()).isEqualTo("super-agent-business");
    }
}
