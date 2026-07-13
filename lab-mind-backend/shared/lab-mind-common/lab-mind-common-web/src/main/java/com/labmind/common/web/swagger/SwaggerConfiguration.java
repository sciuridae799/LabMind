package com.labmind.common.web.swagger;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.util.Assert;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(GroupedOpenApi.class)
public class SwaggerConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "labMindGroupedOpenApi")
    public GroupedOpenApi labMindGroupedOpenApi() {
        return GroupedOpenApi.builder()
                .group("default")
                .pathsToMatch("/**")
                .build();
    }

    @Bean
    @ConditionalOnProperty(name = "spring.application.name")
    @ConditionalOnMissingBean(OpenAPI.class)
    public OpenAPI labMindOpenApi(Environment environment) {
        String applicationName = environment.getRequiredProperty("spring.application.name");
        Assert.hasText(applicationName, "spring.application.name must not be blank");
        return new OpenAPI().info(new Info().title(applicationName.trim()));
    }
}
