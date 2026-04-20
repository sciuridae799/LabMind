package com.superagent.common.web.config;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SuperAgentCommonAutoConfigTest {

    @Test
    void shouldRegisterAutoConfigurationImport() throws Exception {
        try (var inputStream = getClass().getClassLoader().getResourceAsStream(
                "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")) {
            assertThat(inputStream).isNotNull();
            String imports = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(imports).contains("com.superagent.common.web.config.SuperAgentCommonAutoConfig");
        }
    }
}
