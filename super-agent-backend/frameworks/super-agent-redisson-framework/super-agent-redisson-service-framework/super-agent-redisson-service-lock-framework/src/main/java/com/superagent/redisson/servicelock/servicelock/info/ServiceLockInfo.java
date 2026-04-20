package com.superagent.redisson.servicelock.servicelock.info;

import com.superagent.redisson.servicelock.core.LockType;
import java.util.concurrent.TimeUnit;

public record ServiceLockInfo(
        String lockName,
        LockType lockType,
        long waitTime,
        long leaseTime,
        TimeUnit timeUnit) {
}
