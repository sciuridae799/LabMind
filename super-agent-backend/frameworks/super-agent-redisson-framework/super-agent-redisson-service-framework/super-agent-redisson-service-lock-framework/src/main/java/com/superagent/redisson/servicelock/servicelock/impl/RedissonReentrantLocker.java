package com.superagent.redisson.servicelock.servicelock.impl;

import com.superagent.redisson.servicelock.core.LockType;
import com.superagent.redisson.servicelock.core.ServiceLocker;
import java.util.concurrent.TimeUnit;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.util.Assert;

public class RedissonReentrantLocker implements ServiceLocker {

    private final RedissonClient redissonClient;

    public RedissonReentrantLocker(RedissonClient redissonClient) {
        Assert.notNull(redissonClient, "redissonClient must not be null");
        this.redissonClient = redissonClient;
    }

    @Override
    public LockType lockType() {
        return LockType.Reentrant;
    }

    @Override
    public boolean tryLock(String lockName, long waitTime, long leaseTime, TimeUnit timeUnit) throws InterruptedException {
        return redissonClient.getLock(lockName).tryLock(waitTime, leaseTime, timeUnit);
    }

    @Override
    public void unlock(String lockName) {
        RLock lock = redissonClient.getLock(lockName);
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
