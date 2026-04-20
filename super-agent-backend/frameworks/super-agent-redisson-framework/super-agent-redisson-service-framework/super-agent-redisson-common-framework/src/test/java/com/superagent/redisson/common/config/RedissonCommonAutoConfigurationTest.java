package com.superagent.redisson.common.config;

import com.superagent.redisson.common.handler.RedissonDataHandle;
import com.superagent.redisson.common.locallock.LocalLockCache;
import com.superagent.redisson.common.lockinfo.factory.LockInfoHandleFactory;
import java.lang.reflect.Method;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RedissonCommonAutoConfigurationTest {

    @Test
    void shouldBuildSingleServerConfigWhenConfigurationIsValid() throws Exception {
        RedisProperties redisProperties = new RedisProperties();
        redisProperties.setHost("127.0.0.1");
        redisProperties.setPort(6379);
        redisProperties.setDatabase(3);
        redisProperties.setUsername("worker");
        redisProperties.setPassword("secret");
        redisProperties.setClientName("super-agent");
        redisProperties.setConnectTimeout(Duration.ofSeconds(2));
        redisProperties.setTimeout(Duration.ofSeconds(4));

        RedissonBaseProperties redissonBaseProperties = new RedissonBaseProperties();
        redissonBaseProperties.setProtocol("rediss");
        redissonBaseProperties.setThreads(8);
        redissonBaseProperties.setNettyThreads(4);
        redissonBaseProperties.setLocalLockCacheTtl(Duration.ofSeconds(30));

        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.data.redis.host", "127.0.0.1")
                .withProperty("spring.data.redis.port", "6379");

        RedissonCommonAutoConfiguration autoConfiguration = new RedissonCommonAutoConfiguration();
        Config config = autoConfiguration.createConfig(redisProperties, redissonBaseProperties, environment);
        SingleServerConfig singleServerConfig = extractSingleServerConfig(config);

        assertThat(config.getThreads()).isEqualTo(8);
        assertThat(config.getNettyThreads()).isEqualTo(4);
        assertThat(singleServerConfig.getAddress()).isEqualTo("rediss://127.0.0.1:6379");
        assertThat(singleServerConfig.getDatabase()).isEqualTo(3);
        assertThat(singleServerConfig.getUsername()).isEqualTo("worker");
        assertThat(singleServerConfig.getPassword()).isEqualTo("secret");
        assertThat(singleServerConfig.getClientName()).isEqualTo("super-agent");
        assertThat(singleServerConfig.getConnectTimeout()).isEqualTo(2000);
        assertThat(singleServerConfig.getTimeout()).isEqualTo(4000);
    }

    @Test
    void shouldFailWhenRedisHostIsMissing() {
        RedisProperties redisProperties = new RedisProperties();
        redisProperties.setPort(6379);

        RedissonBaseProperties redissonBaseProperties = new RedissonBaseProperties();
        redissonBaseProperties.setProtocol("redis");
        redissonBaseProperties.setLocalLockCacheTtl(Duration.ofSeconds(30));

        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.data.redis.port", "6379");

        RedissonCommonAutoConfiguration autoConfiguration = new RedissonCommonAutoConfiguration();
        assertThatThrownBy(() -> autoConfiguration.createConfig(redisProperties, redissonBaseProperties, environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.data.redis.host");
    }

    @Test
    void shouldFailWhenRedisPortIsMissing() {
        RedisProperties redisProperties = new RedisProperties();
        redisProperties.setHost("127.0.0.1");

        RedissonBaseProperties redissonBaseProperties = new RedissonBaseProperties();
        redissonBaseProperties.setProtocol("redis");
        redissonBaseProperties.setLocalLockCacheTtl(Duration.ofSeconds(30));

        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.data.redis.host", "127.0.0.1");

        RedissonCommonAutoConfiguration autoConfiguration = new RedissonCommonAutoConfiguration();
        assertThatThrownBy(() -> autoConfiguration.createConfig(redisProperties, redissonBaseProperties, environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.data.redis.port");
    }

    @Test
    void shouldFailWhenRedisPortIsNotANumber() {
        RedisProperties redisProperties = new RedisProperties();
        redisProperties.setHost("127.0.0.1");

        RedissonBaseProperties redissonBaseProperties = new RedissonBaseProperties();
        redissonBaseProperties.setProtocol("redis");
        redissonBaseProperties.setLocalLockCacheTtl(Duration.ofSeconds(30));

        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.data.redis.host", "127.0.0.1")
                .withProperty("spring.data.redis.port", "bad-port");

        RedissonCommonAutoConfiguration autoConfiguration = new RedissonCommonAutoConfiguration();
        assertThatThrownBy(() -> autoConfiguration.createConfig(redisProperties, redissonBaseProperties, environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.data.redis.port")
                .hasMessageContaining("valid integer");
    }

    @Test
    void shouldFailWhenProtocolIsInvalid() {
        RedisProperties redisProperties = new RedisProperties();
        redisProperties.setHost("127.0.0.1");
        redisProperties.setPort(6379);

        RedissonBaseProperties redissonBaseProperties = new RedissonBaseProperties();
        redissonBaseProperties.setProtocol("http");
        redissonBaseProperties.setLocalLockCacheTtl(Duration.ofSeconds(30));

        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.data.redis.host", "127.0.0.1")
                .withProperty("spring.data.redis.port", "6379");

        RedissonCommonAutoConfiguration autoConfiguration = new RedissonCommonAutoConfiguration();
        assertThatThrownBy(() -> autoConfiguration.createConfig(redisProperties, redissonBaseProperties, environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.redis.redisson.protocol");
    }

    @Test
    void shouldFailWhenLocalLockCacheTtlIsInvalid() {
        RedissonCommonAutoConfiguration autoConfiguration = new RedissonCommonAutoConfiguration();
        RedissonBaseProperties redissonBaseProperties = new RedissonBaseProperties();
        redissonBaseProperties.setLocalLockCacheTtl(Duration.ZERO);

        assertThatThrownBy(() -> autoConfiguration.localLockCache(redissonBaseProperties))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.redis.redisson.local-lock-cache-ttl");
    }

    @Test
    void shouldCreateHelperBeans() {
        RedissonCommonAutoConfiguration autoConfiguration = new RedissonCommonAutoConfiguration();
        RedissonClient redissonClient = org.mockito.Mockito.mock(RedissonClient.class);
        RedissonBaseProperties redissonBaseProperties = new RedissonBaseProperties();
        redissonBaseProperties.setLocalLockCacheTtl(Duration.ofSeconds(30));

        assertThat(autoConfiguration.redissonDataHandle(redissonClient)).isInstanceOf(RedissonDataHandle.class);
        assertThat(autoConfiguration.localLockCache(redissonBaseProperties)).isInstanceOf(LocalLockCache.class);
        assertThat(autoConfiguration.lockInfoHandleFactory()).isInstanceOf(LockInfoHandleFactory.class);
    }

    @Test
    void shouldRegisterAutoConfigurationImport() throws Exception {
        try (var inputStream = getClass().getClassLoader().getResourceAsStream(
                "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")) {
            assertThat(inputStream).isNotNull();
            String imports = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(imports).contains("com.superagent.redisson.common.config.RedissonCommonAutoConfiguration");
        }
    }

    private SingleServerConfig extractSingleServerConfig(Config config) throws Exception {
        Method method = Config.class.getDeclaredMethod("getSingleServerConfig");
        method.setAccessible(true);
        return (SingleServerConfig) method.invoke(config);
    }
}
