package com.superagent.redisson.common.config;

import com.superagent.redisson.common.handler.RedissonDataHandle;
import com.superagent.redisson.common.locallock.LocalLockCache;
import com.superagent.redisson.common.lockinfo.factory.LockInfoHandleFactory;
import java.time.Duration;
import java.util.Locale;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.util.StringUtils;

@AutoConfiguration
@EnableConfigurationProperties({RedisProperties.class, RedissonBaseProperties.class})
public class RedissonCommonAutoConfiguration {

    private static final String OFFICIAL_REDIS_HOST_KEY = "spring.data.redis.host";
    private static final String OFFICIAL_REDIS_PORT_KEY = "spring.data.redis.port";
    private static final String REDISSON_PROTOCOL_KEY = "spring.redis.redisson.protocol";
    private static final String REDISSON_LOCAL_LOCK_CACHE_TTL_KEY = "spring.redis.redisson.local-lock-cache-ttl";
    private static final String REDISSON_THREADS_KEY = "spring.redis.redisson.threads";
    private static final String REDISSON_NETTY_THREADS_KEY = "spring.redis.redisson.netty-threads";

    @Bean(destroyMethod = "shutdown")
    public RedissonClient redissonClient(
            RedisProperties redisProperties,
            RedissonBaseProperties redissonBaseProperties,
            Environment environment) {
        return Redisson.create(createConfig(redisProperties, redissonBaseProperties, environment));
    }

    Config createConfig(
            RedisProperties redisProperties,
            RedissonBaseProperties redissonBaseProperties,
            Environment environment) {
        String host = requireExplicitTextProperty(environment, OFFICIAL_REDIS_HOST_KEY);
        int port = requireExplicitPort(environment, OFFICIAL_REDIS_PORT_KEY);
        String protocol = resolveProtocol(redissonBaseProperties.getProtocol());

        Config config = new Config();
        configureThreads(config, redissonBaseProperties);

        SingleServerConfig singleServerConfig = config.useSingleServer();
        singleServerConfig.setAddress(protocol + "://" + host + ":" + port);
        singleServerConfig.setDatabase(requireNonNegative(redisProperties.getDatabase(), "spring.data.redis.database"));

        if (StringUtils.hasText(redisProperties.getUsername())) {
            singleServerConfig.setUsername(redisProperties.getUsername().trim());
        }
        if (StringUtils.hasText(redisProperties.getPassword())) {
            singleServerConfig.setPassword(redisProperties.getPassword());
        }
        if (StringUtils.hasText(redisProperties.getClientName())) {
            singleServerConfig.setClientName(redisProperties.getClientName().trim());
        }
        if (redisProperties.getConnectTimeout() != null) {
            singleServerConfig.setConnectTimeout(toMillis(redisProperties.getConnectTimeout(), "spring.data.redis.connect-timeout"));
        }
        if (redisProperties.getTimeout() != null) {
            singleServerConfig.setTimeout(toMillis(redisProperties.getTimeout(), "spring.data.redis.timeout"));
        }

        return config;
    }

    @Bean
    public RedissonDataHandle redissonDataHandle(RedissonClient redissonClient) {
        return new RedissonDataHandle(redissonClient);
    }

    @Bean
    public LocalLockCache localLockCache(RedissonBaseProperties redissonBaseProperties) {
        return new LocalLockCache(requirePositiveDuration(
                redissonBaseProperties.getLocalLockCacheTtl(),
                REDISSON_LOCAL_LOCK_CACHE_TTL_KEY));
    }

    @Bean
    public LockInfoHandleFactory lockInfoHandleFactory() {
        return new LockInfoHandleFactory();
    }

    private void configureThreads(Config config, RedissonBaseProperties redissonBaseProperties) {
        if (redissonBaseProperties.getThreads() != null) {
            config.setThreads(requirePositive(redissonBaseProperties.getThreads(), REDISSON_THREADS_KEY));
        }
        if (redissonBaseProperties.getNettyThreads() != null) {
            config.setNettyThreads(requirePositive(redissonBaseProperties.getNettyThreads(), REDISSON_NETTY_THREADS_KEY));
        }
    }

    private String requireExplicitTextProperty(Environment environment, String propertyKey) {
        String value = environment.getProperty(propertyKey);
        if (StringUtils.hasText(value)) {
            return value.trim();
        }
        throw new IllegalStateException(
                "Redis single-server configuration requires an explicit property: " + propertyKey + ".");
    }

    private int requireExplicitPort(Environment environment, String propertyKey) {
        String value = requireExplicitTextProperty(environment, propertyKey);
        try {
            return requirePositive(Integer.parseInt(value), propertyKey);
        }
        catch (NumberFormatException ex) {
            throw new IllegalStateException(propertyKey + " must be a valid integer.", ex);
        }
    }

    private String resolveProtocol(String protocol) {
        if (!StringUtils.hasText(protocol)) {
            throw new IllegalStateException(REDISSON_PROTOCOL_KEY + " must be configured.");
        }

        String normalizedProtocol = protocol.trim().toLowerCase(Locale.ROOT);
        if (!"redis".equals(normalizedProtocol) && !"rediss".equals(normalizedProtocol)) {
            throw new IllegalStateException(
                    REDISSON_PROTOCOL_KEY + " only supports redis or rediss, but got: " + protocol);
        }
        return normalizedProtocol;
    }

    private Duration requirePositiveDuration(Duration duration, String propertyName) {
        if (duration == null) {
            throw new IllegalStateException(propertyName + " must be configured.");
        }
        if (duration.isZero() || duration.isNegative()) {
            throw new IllegalStateException(propertyName + " must be greater than 0.");
        }
        return duration;
    }

    private int toMillis(Duration duration, String propertyName) {
        Duration validatedDuration = requirePositiveDuration(duration, propertyName);
        try {
            return Math.toIntExact(validatedDuration.toMillis());
        }
        catch (ArithmeticException ex) {
            throw new IllegalStateException(propertyName + " is too large to convert to milliseconds.", ex);
        }
    }

    private int requirePositive(Integer value, String propertyName) {
        if (value == null || value <= 0) {
            throw new IllegalStateException(propertyName + " must be greater than 0.");
        }
        return value;
    }

    private int requireNonNegative(Integer value, String propertyName) {
        if (value == null || value < 0) {
            throw new IllegalStateException(propertyName + " must be greater than or equal to 0.");
        }
        return value;
    }
}
