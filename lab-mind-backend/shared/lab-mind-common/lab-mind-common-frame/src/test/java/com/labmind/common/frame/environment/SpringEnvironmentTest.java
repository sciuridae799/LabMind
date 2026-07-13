package com.labmind.common.frame.environment;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

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
    void shouldLoadDotEnvPropertiesFromNearestAncestorDirectory(@TempDir Path tempDir) throws Exception {
        Path rootDirectory = tempDir.resolve("workspace");
        Path nestedDirectory = rootDirectory.resolve("lab-mind-backend/services");
        Files.createDirectories(nestedDirectory);
        Files.writeString(
                rootDirectory.resolve(SpringEnvironment.DOT_ENV_FILE_NAME),
                """
                ALI_BAI_LIAN_API_KEY=test-bai-lian-key
                export TAVILY_API_KEY="test-tavily-key"
                """);

        Map<String, Object> properties = SpringEnvironment.loadDotEnvProperties(nestedDirectory);

        assertThat(properties)
                .containsEntry("ALI_BAI_LIAN_API_KEY", "test-bai-lian-key")
                .containsEntry("TAVILY_API_KEY", "test-tavily-key");
    }

    @Test
    void shouldExposeDotEnvPropertiesToEnvironment(@TempDir Path tempDir) throws Exception {
        Path nestedDirectory = tempDir.resolve("workspace/module");
        Files.createDirectories(nestedDirectory);
        Files.writeString(
                tempDir.resolve("workspace").resolve(SpringEnvironment.DOT_ENV_FILE_NAME),
                "ALI_BAI_LIAN_API_KEY=test-bai-lian-key");
        MockEnvironment environment = new MockEnvironment();

        new SpringEnvironment().postProcessEnvironment(environment, nestedDirectory);

        assertThat(environment.getProperty("ALI_BAI_LIAN_API_KEY")).isEqualTo("test-bai-lian-key");
    }

    @Test
    void shouldExposeDotEnvPropertiesUsingSpringEnvironmentVariableBinding(@TempDir Path tempDir) throws Exception {
        Path nestedDirectory = tempDir.resolve("workspace/module");
        Files.createDirectories(nestedDirectory);
        Files.writeString(
                tempDir.resolve("workspace").resolve(SpringEnvironment.DOT_ENV_FILE_NAME),
                """
                SPRING_DATASOURCE_URL=jdbc:mysql://127.0.0.1:3306/service_db
                SPRING_DATA_REDIS_HOST=127.0.0.1
                """);
        MockEnvironment environment = new MockEnvironment();

        new SpringEnvironment().postProcessEnvironment(environment, nestedDirectory);

        assertThat(environment.getProperty("spring.datasource.url"))
                .isEqualTo("jdbc:mysql://127.0.0.1:3306/service_db");
        assertThat(environment.getProperty("spring.data.redis.host")).isEqualTo("127.0.0.1");
    }

    @Test
    void shouldFailWhenDotEnvContainsInvalidEntry(@TempDir Path tempDir) throws Exception {
        Files.writeString(
                tempDir.resolve(SpringEnvironment.DOT_ENV_FILE_NAME),
                """
                ALI_BAI_LIAN_API_KEY=test-bai-lian-key
                invalid-line
                """);

        assertThatIllegalStateException()
                .isThrownBy(() -> SpringEnvironment.loadDotEnvProperties(tempDir))
                .withMessageContaining("Invalid .env entry");
    }

    @Test
    void shouldRegisterEnvironmentPostProcessorInSpringFactories() throws Exception {
        try (var inputStream = getClass().getClassLoader().getResourceAsStream("META-INF/spring.factories")) {
            assertThat(inputStream).isNotNull();
            String factories = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(factories).contains("com.labmind.common.frame.environment.SpringEnvironment");
        }
    }
}
