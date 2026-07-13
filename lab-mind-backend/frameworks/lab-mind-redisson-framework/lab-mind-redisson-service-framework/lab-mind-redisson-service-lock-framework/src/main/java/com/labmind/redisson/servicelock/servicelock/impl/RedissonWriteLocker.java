package com.labmind.redisson.servicelock.servicelock.impl;

import com.labmind.redisson.servicelock.core.LockType;
import com.labmind.redisson.servicelock.core.ServiceLocker;
import java.util.concurrent.TimeUnit;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.util.Assert;

public class RedissonWriteLocker implements ServiceLocker {

    private final RedissonClient redissonClient;

    public RedissonWriteLocker(RedissonClient redissonClient) {
        Assert.notNull(redissonClient, "redissonClient must not be null");
        this.redissonClient = redissonClient;
    }

    @Override
    public LockType lockType() {
        return LockType.Write;
    }

    @Override
    public boolean tryLock(String lockName, long waitTime, long leaseTime, TimeUnit timeUnit) throws InterruptedException {
        return redissonClient.getReadWriteLock(lockName).writeLock().tryLock(waitTime, leaseTime, timeUnit);
    }

    @Override
    public void unlock(String lockName) {
        RLock lock = redissonClient.getReadWriteLock(lockName).writeLock();
        if (lock.isHeldByCurrentThread()) {
            lock.unlock();
        }
    }
}
