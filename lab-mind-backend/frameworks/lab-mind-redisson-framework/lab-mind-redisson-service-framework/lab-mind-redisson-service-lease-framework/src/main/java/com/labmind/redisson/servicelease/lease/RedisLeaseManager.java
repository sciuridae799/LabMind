package com.labmind.redisson.servicelease.lease;

import java.time.Duration;
import java.util.List;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

public class RedisLeaseManager {

    private static final String ACQUIRE_SCRIPT = """
            if redis.call('exists', KEYS[1]) == 0 then
                redis.call('psetex', KEYS[1], ARGV[2], ARGV[1])
                return 1
            end
            return 0
            """;

    private static final String RENEW_SCRIPT = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                redis.call('pexpire', KEYS[1], ARGV[2])
                return 1
            end
            return 0
            """;

    private static final String RELEASE_SCRIPT = """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                redis.call('del', KEYS[1])
                return 1
            end
            return 0
            """;

    private final RedissonClient redissonClient;

    public RedisLeaseManager(RedissonClient redissonClient) {
        Assert.notNull(redissonClient, "redissonClient must not be null");
        this.redissonClient = redissonClient;
    }

    public boolean acquire(String leaseKey, String ownerToken, Duration ttl) {
        return executeLeaseScript(ACQUIRE_SCRIPT, leaseKey, ownerToken, ttl) == 1L;
    }

    public boolean renew(String leaseKey, String ownerToken, Duration ttl) {
        return executeLeaseScript(RENEW_SCRIPT, leaseKey, ownerToken, ttl) == 1L;
    }

    public boolean release(String leaseKey, String ownerToken) {
        validateLeaseKey(leaseKey);
        validateOwnerToken(ownerToken);
        return executeIntegerScript(RELEASE_SCRIPT, leaseKey, ownerToken) == 1L;
    }

    private long executeLeaseScript(String script, String leaseKey, String ownerToken, Duration ttl) {
        validateLeaseKey(leaseKey);
        validateOwnerToken(ownerToken);
        validateTtl(ttl);
        return executeIntegerScript(script, leaseKey, ownerToken, ttl.toMillis());
    }

    private long executeIntegerScript(String script, String leaseKey, Object... arguments) {
        Object result = redissonClient.getScript(StringCodec.INSTANCE).eval(
                RScript.Mode.READ_WRITE,
                script,
                RScript.ReturnType.INTEGER,
                List.of(leaseKey),
                arguments);
        if (!(result instanceof Number number)) {
            throw new IllegalStateException("Redis lease script returned an unexpected result: " + result);
        }
        return number.longValue();
    }

    private void validateLeaseKey(String leaseKey) {
        if (!StringUtils.hasText(leaseKey)) {
            throw new IllegalArgumentException("leaseKey must not be blank");
        }
    }

    private void validateOwnerToken(String ownerToken) {
        if (!StringUtils.hasText(ownerToken)) {
            throw new IllegalArgumentException("ownerToken must not be blank");
        }
    }

    private void validateTtl(Duration ttl) {
        Assert.notNull(ttl, "ttl must not be null");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be greater than 0");
        }
    }
}
