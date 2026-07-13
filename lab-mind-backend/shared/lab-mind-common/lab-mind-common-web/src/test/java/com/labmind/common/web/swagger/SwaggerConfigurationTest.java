package com.labmind.common.web.swagger;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

class SwaggerConfigurationTest {

    @Test
    void shouldCreateDefaultOpenApiGroup() {
        GroupedOpenApi groupedOpenApi = new SwaggerConfiguration().labMindGroupedOpenApi();

        assertThat(groupedOpenApi.getGroup()).isEqualTo("default");
    }

    @Test
    void shouldCreateOpenApiInfoFromApplicationName() {
        MockEnvironment environment = new MockEnvironment().withProperty("spring.application.name", "lab-mind-business");

        OpenAPI openAPI = new SwaggerConfiguration().labMindOpenApi(environment);

        assertThat(openAPI.getInfo()).isNotNull();
        assertThat(openAPI.getInfo().getTitle()).isEqualTo("lab-mind-business");
    }
}
