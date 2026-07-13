package com.labmind.integration.external;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "integration.external")
public class ExternalServiceIntegrationProperties {

    @Valid
    private final JdbcServiceProperties mysql = new JdbcServiceProperties();

    @Valid
    private final RedisProperties redis = new RedisProperties();

    @Valid
    private final KafkaProperties kafka = new KafkaProperties();

    @Valid
    private final MinioProperties minio = new MinioProperties();

    @Valid
    private final JdbcServiceProperties pgvector = new JdbcServiceProperties();

    @Valid
    private final EndpointProperties elasticsearch = new EndpointProperties();

    @Valid
    private final AuthenticatedEndpointProperties neo4j = new AuthenticatedEndpointProperties();

    public JdbcServiceProperties getMysql() {
        return mysql;
    }

    public RedisProperties getRedis() {
        return redis;
    }

    public KafkaProperties getKafka() {
        return kafka;
    }

    public MinioProperties getMinio() {
        return minio;
    }

    public JdbcServiceProperties getPgvector() {
        return pgvector;
    }

    public EndpointProperties getElasticsearch() {
        return elasticsearch;
    }

    public AuthenticatedEndpointProperties getNeo4j() {
        return neo4j;
    }

    public static class JdbcServiceProperties {
        @NotBlank
        private String jdbcUrl;

        @NotBlank
        private String username;

        @NotBlank
        private String password;

        public String getJdbcUrl() {
            return jdbcUrl;
        }

        public void setJdbcUrl(String jdbcUrl) {
            this.jdbcUrl = jdbcUrl;
        }

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }

    public static class RedisProperties {
        @NotBlank
        private String uri;

        public String getUri() {
            return uri;
        }

        public void setUri(String uri) {
            this.uri = uri;
        }
    }

    public static class KafkaProperties {
        @NotBlank
        private String bootstrapServers;

        @NotBlank
        private String topic;

        public String getBootstrapServers() {
            return bootstrapServers;
        }

        public void setBootstrapServers(String bootstrapServers) {
            this.bootstrapServers = bootstrapServers;
        }

        public String getTopic() {
            return topic;
        }

        public void setTopic(String topic) {
            this.topic = topic;
        }
    }

    public static class MinioProperties {
        @NotBlank
        private String endpoint;

        @NotBlank
        private String accessKey;

        @NotBlank
        private String secretKey;

        @NotBlank
        private String bucket;

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }

        public String getAccessKey() {
            return accessKey;
        }

        public void setAccessKey(String accessKey) {
            this.accessKey = accessKey;
        }

        public String getSecretKey() {
            return secretKey;
        }

        public void setSecretKey(String secretKey) {
            this.secretKey = secretKey;
        }

        public String getBucket() {
            return bucket;
        }

        public void setBucket(String bucket) {
            this.bucket = bucket;
        }
    }

    public static class EndpointProperties {
        @NotBlank
        private String endpoint;

        public String getEndpoint() {
            return endpoint;
        }

        public void setEndpoint(String endpoint) {
            this.endpoint = endpoint;
        }
    }

    public static class AuthenticatedEndpointProperties extends EndpointProperties {
        @NotBlank
        private String username;

        @NotBlank
        private String password;

        public String getUsername() {
            return username;
        }

        public void setUsername(String username) {
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }
    }
}
