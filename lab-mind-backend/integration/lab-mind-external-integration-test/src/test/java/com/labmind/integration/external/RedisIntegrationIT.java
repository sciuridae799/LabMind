package com.labmind.integration.external;

import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import java.time.Duration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RedisIntegrationIT extends AbstractExternalIntegrationIT {

    @Test
    void shouldSetExpireReadAndDeleteRedisValue() {
        String key = runId("integration:redis");
        String value = runId("payload");

        try (RedisClient redisClient = RedisClient.create(properties.getRedis().getUri());
             StatefulRedisConnection<String, String> connection = redisClient.connect()) {
            var commands = connection.sync();

            assertThat(commands.set(key, value)).isEqualTo("OK");
            assertThat(commands.expire(key, Duration.ofSeconds(30).toSeconds())).isTrue();
            assertThat(commands.get(key)).isEqualTo(value);
            assertThat(commands.del(key)).isEqualTo(1L);
            assertThat(commands.get(key)).isNull();
        }
    }
}
