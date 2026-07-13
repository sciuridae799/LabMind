package com.labmind.common.frame.environment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.util.StringUtils;

public class SpringEnvironment implements EnvironmentPostProcessor, Ordered {

    static final String ALLOW_BEAN_DEFINITION_OVERRIDING = "spring.main.allow-bean-definition-overriding";
    static final String DOT_ENV_FILE_NAME = ".env";

    private static final String PROPERTY_SOURCE_NAME = "labMindCommonFrameEnvironment";
    private static final String DOT_ENV_PROPERTY_SOURCE_NAME = "labMindDotEnvEnvironment";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        postProcessEnvironment(environment, resolveWorkingDirectory());
    }

    void postProcessEnvironment(ConfigurableEnvironment environment, Path workingDirectory) {
        Map<String, Object> dotEnvProperties = loadDotEnvProperties(workingDirectory);
        if (!dotEnvProperties.isEmpty()) {
            environment.getPropertySources().addLast(new SystemEnvironmentPropertySource(
                    DOT_ENV_PROPERTY_SOURCE_NAME, dotEnvProperties));
        }
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

    static Map<String, Object> loadDotEnvProperties(Path workingDirectory) {
        Optional<Path> dotEnvPath = findDotEnvPath(workingDirectory);
        if (dotEnvPath.isEmpty()) {
            return Map.of();
        }
        return parseDotEnv(dotEnvPath.get());
    }

    static Optional<Path> findDotEnvPath(Path workingDirectory) {
        Path current = workingDirectory.toAbsolutePath().normalize();
        while (current != null) {
            Path candidate = current.resolve(DOT_ENV_FILE_NAME);
            if (Files.isRegularFile(candidate)) {
                return Optional.of(candidate);
            }
            current = current.getParent();
        }
        return Optional.empty();
    }

    private static Path resolveWorkingDirectory() {
        return Path.of(System.getProperty("user.dir"));
    }

    private static Map<String, Object> parseDotEnv(Path dotEnvPath) {
        LinkedHashMap<String, Object> properties = new LinkedHashMap<>();
        try {
            int lineNumber = 0;
            for (String rawLine : Files.readAllLines(dotEnvPath, StandardCharsets.UTF_8)) {
                lineNumber++;
                String line = rawLine.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                if (line.startsWith("export ")) {
                    line = line.substring("export ".length()).trim();
                }

                int separatorIndex = line.indexOf('=');
                if (separatorIndex <= 0) {
                    throw invalidDotEnv(dotEnvPath, lineNumber, "expected KEY=VALUE");
                }

                String key = line.substring(0, separatorIndex).trim();
                if (!StringUtils.hasText(key)) {
                    throw invalidDotEnv(dotEnvPath, lineNumber, "key must not be blank");
                }
                if (containsWhitespace(key)) {
                    throw invalidDotEnv(dotEnvPath, lineNumber, "key must not contain whitespace");
                }
                if (properties.containsKey(key)) {
                    throw invalidDotEnv(dotEnvPath, lineNumber, "duplicate key: " + key);
                }

                String value = normalizeValue(line.substring(separatorIndex + 1).trim());
                properties.put(key, value);
            }
            return properties;
        }
        catch (IOException ex) {
            throw new IllegalStateException("Failed to read .env file: " + dotEnvPath, ex);
        }
    }

    private static boolean containsWhitespace(String key) {
        for (int index = 0; index < key.length(); index++) {
            if (Character.isWhitespace(key.charAt(index))) {
                return true;
            }
        }
        return false;
    }

    private static String normalizeValue(String rawValue) {
        if (rawValue.length() >= 2) {
            boolean doubleQuoted = rawValue.startsWith("\"") && rawValue.endsWith("\"");
            boolean singleQuoted = rawValue.startsWith("'") && rawValue.endsWith("'");
            if (doubleQuoted || singleQuoted) {
                return rawValue.substring(1, rawValue.length() - 1);
            }
        }
        return rawValue;
    }

    private static IllegalStateException invalidDotEnv(Path dotEnvPath, int lineNumber, String reason) {
        return new IllegalStateException(
                "Invalid .env entry at " + dotEnvPath + ":" + lineNumber + " - " + reason + ".");
    }
}
