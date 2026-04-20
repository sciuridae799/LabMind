package com.superagent.redisson.servicelock.lockinfo.impl;

import com.superagent.redisson.common.lockinfo.factory.LockInfoHandleFactory;
import com.superagent.redisson.servicelock.servicelock.annotation.ServiceLock;
import com.superagent.redisson.servicelock.servicelock.info.ServiceLockInfo;
import com.superagent.redisson.servicelock.util.ServiceLockKeyEvaluator;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.util.Assert;

public class ServiceLockInfoHandle {

    private final LockInfoHandleFactory lockInfoHandleFactory;

    private final ServiceLockKeyEvaluator serviceLockKeyEvaluator;

    public ServiceLockInfoHandle(
            LockInfoHandleFactory lockInfoHandleFactory,
            ServiceLockKeyEvaluator serviceLockKeyEvaluator) {
        Assert.notNull(lockInfoHandleFactory, "lockInfoHandleFactory must not be null");
        Assert.notNull(serviceLockKeyEvaluator, "serviceLockKeyEvaluator must not be null");
        this.lockInfoHandleFactory = lockInfoHandleFactory;
        this.serviceLockKeyEvaluator = serviceLockKeyEvaluator;
    }

    public ServiceLockInfo create(Method method, Object[] arguments, ServiceLock serviceLock) {
        Assert.notNull(method, "method must not be null");
        Assert.notNull(serviceLock, "serviceLock must not be null");
        validateLockTime(serviceLock.waitTime(), serviceLock.leaseTime(), serviceLock.timeUnit());
        List<String> lockKeys = serviceLockKeyEvaluator.resolveKeys(method, arguments, serviceLock.keys());
        String namespace = method.getDeclaringClass().getName() + "." + method.getName();
        String lockName = lockInfoHandleFactory.createLockName(namespace, lockKeys);
        return new ServiceLockInfo(
                lockName,
                serviceLock.lockType(),
                serviceLock.waitTime(),
                serviceLock.leaseTime(),
                serviceLock.timeUnit());
    }

    private void validateLockTime(long waitTime, long leaseTime, TimeUnit timeUnit) {
        if (waitTime < 0) {
            throw new IllegalStateException("ServiceLock.waitTime must be greater than or equal to 0.");
        }
        if (leaseTime <= 0) {
            throw new IllegalStateException("ServiceLock.leaseTime must be greater than 0.");
        }
        if (timeUnit == null) {
            throw new IllegalStateException("ServiceLock.timeUnit must not be null.");
        }
    }
}
