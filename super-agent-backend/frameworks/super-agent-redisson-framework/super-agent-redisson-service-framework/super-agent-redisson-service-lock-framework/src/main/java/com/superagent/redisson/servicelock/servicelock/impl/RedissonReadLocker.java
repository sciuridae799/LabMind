package com.superagent.redisson.servicelock.servicelock.impl;

import com.superagent.redisson.servicelock.core.LockType;
import com.superagent.redisson.servicelock.core.ServiceLocker;
import java.util.concurrent.TimeUnit;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.util.Assert;

public class RedissonReadLocker implements ServiceLocker {

    private final RedissonClient redissonClient;

    public RedissonReadLocker(RedissonClient redissonClient) {
        Assert.notNull(redissonClient, "redissonClient must not be null");
        this.redissonClient = redissonClient;
    }

    @Override
    public LockType lockType() {
        return LockType.Read;
    }

    @Override
    public boolean tryLock(String lockName, long waitTime, long leaseTime, TimeUnit timeUnit) throws InterruptedException {
        return redissonClient.getReadWriteLock(lockName).readLock().tryLock(waitTime, leaseTime, timeUnit);
    }

    @Override
    public void unlock(String lockName) {
        RLock lock = redissonClient.getReadWriteLock(lockName).readLock();
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
