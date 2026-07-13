package com.labmind.redisson.common.handler;

import java.time.Duration;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

public class RedissonDataHandle {

    private final RedissonClient redissonClient;

    public RedissonDataHandle(RedissonClient redissonClient) {
        Assert.notNull(redissonClient, "redissonClient must not be null");
        this.redissonClient = redissonClient;
    }

    public Object get(String key) {
        return bucket(key).get();
    }

    public <T> T get(String key, Class<T> targetType) {
        Assert.notNull(targetType, "targetType must not be null");
        Object value = bucket(key).get();
        if (value == null) {
            return null;
        }
        if (!targetType.isInstance(value)) {
            throw new IllegalStateException(
                    "Redis value type mismatch for key "
                            + key
                            + ": expected "
                            + targetType.getName()
                            + " but was "
                            + value.getClass().getName());
        }
        return targetType.cast(value);
    }

    public void set(String key, Object value) {
        Assert.notNull(value, "value must not be null");
        bucket(key).set(value);
    }

    public void set(String key, Object value, Duration ttl) {
        Assert.notNull(value, "value must not be null");
        Assert.notNull(ttl, "ttl must not be null");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be greater than 0");
        }
        bucket(key).set(value, ttl);
    }

    public boolean exists(String key) {
        return bucket(key).isExists();
    }

    public boolean delete(String key) {
        return bucket(key).delete();
    }

    private RBucket<Object> bucket(String key) {
        if (!StringUtils.hasText(key)) {
            throw new IllegalArgumentException("key must not be blank");
        }
        return redissonClient.getBucket(key.trim());
    }
}
