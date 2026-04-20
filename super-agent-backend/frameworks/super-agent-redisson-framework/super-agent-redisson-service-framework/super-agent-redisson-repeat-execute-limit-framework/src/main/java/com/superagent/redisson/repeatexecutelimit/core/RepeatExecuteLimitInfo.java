package com.superagent.redisson.repeatexecutelimit.core;

import java.util.concurrent.TimeUnit;

public record RepeatExecuteLimitInfo(
        String successKey,
        String localLockKey,
        String distributedLockName,
        long waitTime,
        long lockLeaseTime,
        long successLeaseTime,
        TimeUnit timeUnit) {
}
