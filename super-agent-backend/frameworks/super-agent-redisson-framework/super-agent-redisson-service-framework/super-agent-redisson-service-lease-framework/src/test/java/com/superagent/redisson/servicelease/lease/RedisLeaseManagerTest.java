package com.superagent.redisson.servicelease.lease;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.Codec;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisLeaseManagerTest {

    @Test
    void shouldAcquireRenewAndReleaseLease() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        RScript script = mock(RScript.class);
        when(redissonClient.getScript(any(Codec.class))).thenReturn(script);
        when(script.eval(
                eq(RScript.Mode.READ_WRITE),
                Mockito.contains("psetex"),
                eq(RScript.ReturnType.INTEGER),
                eq(List.of("lease:test")),
                eq("owner"),
                eq(5000L)))
                .thenReturn(1L);
        when(script.eval(
                eq(RScript.Mode.READ_WRITE),
                Mockito.contains("pexpire"),
                eq(RScript.ReturnType.INTEGER),
                eq(List.of("lease:test")),
                eq("owner"),
                eq(5000L)))
                .thenReturn(1L);
        when(script.eval(
                eq(RScript.Mode.READ_WRITE),
                Mockito.contains("del"),
                eq(RScript.ReturnType.INTEGER),
                eq(List.of("lease:test")),
                eq("owner")))
                .thenReturn(1L);

        RedisLeaseManager redisLeaseManager = new RedisLeaseManager(redissonClient);

        assertThat(redisLeaseManager.acquire("lease:test", "owner", Duration.ofSeconds(5))).isTrue();
        assertThat(redisLeaseManager.renew("lease:test", "owner", Duration.ofSeconds(5))).isTrue();
        assertThat(redisLeaseManager.release("lease:test", "owner")).isTrue();
    }

    @Test
    void shouldRejectInvalidTtl() {
        RedisLeaseManager redisLeaseManager = new RedisLeaseManager(mock(RedissonClient.class));

        assertThatThrownBy(() -> redisLeaseManager.acquire("lease:test", "owner", Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ttl");
    }
}
