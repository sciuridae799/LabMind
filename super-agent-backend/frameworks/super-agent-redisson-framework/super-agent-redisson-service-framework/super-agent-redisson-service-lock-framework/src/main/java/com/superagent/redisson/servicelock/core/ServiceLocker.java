package com.superagent.redisson.servicelock.core;

import java.util.concurrent.TimeUnit;

public interface ServiceLocker {

    LockType lockType();

    boolean tryLock(String lockName, long waitTime, long leaseTime, TimeUnit timeUnit) throws InterruptedException;

    void unlock(String lockName);
}
