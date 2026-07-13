package com.labmind.redisson.servicelock.servicelock.aspect;

import com.labmind.redisson.servicelock.core.ServiceLocker;
import com.labmind.redisson.servicelock.lockinfo.impl.ServiceLockInfoHandle;
import com.labmind.redisson.servicelock.servicelock.annotation.ServiceLock;
import com.labmind.redisson.servicelock.servicelock.factory.ServiceLockFactory;
import com.labmind.redisson.servicelock.servicelock.info.ServiceLockInfo;
import java.lang.reflect.Method;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.util.Assert;

@Aspect
public class ServiceLockAspect {

    private final ServiceLockInfoHandle serviceLockInfoHandle;

    private final ServiceLockFactory serviceLockFactory;

    public ServiceLockAspect(ServiceLockInfoHandle serviceLockInfoHandle, ServiceLockFactory serviceLockFactory) {
        Assert.notNull(serviceLockInfoHandle, "serviceLockInfoHandle must not be null");
        Assert.notNull(serviceLockFactory, "serviceLockFactory must not be null");
        this.serviceLockInfoHandle = serviceLockInfoHandle;
        this.serviceLockFactory = serviceLockFactory;
    }

    @Around("@annotation(serviceLock)")
    public Object around(ProceedingJoinPoint joinPoint, ServiceLock serviceLock) throws Throwable {
        Method method = resolveMethod(joinPoint);
        ServiceLockInfo serviceLockInfo = serviceLockInfoHandle.create(method, joinPoint.getArgs(), serviceLock);
        ServiceLocker serviceLocker = serviceLockFactory.getLocker(serviceLockInfo.lockType());
        boolean locked = serviceLocker.tryLock(
                serviceLockInfo.lockName(),
                serviceLockInfo.waitTime(),
                serviceLockInfo.leaseTime(),
                serviceLockInfo.timeUnit());
        if (!locked) {
            throw new IllegalStateException("Failed to acquire service lock: " + serviceLockInfo.lockName());
        }

        try {
            return joinPoint.proceed();
        }
        finally {
            serviceLocker.unlock(serviceLockInfo.lockName());
        }
    }

    private Method resolveMethod(ProceedingJoinPoint joinPoint) {
        MethodSignature methodSignature = (MethodSignature) joinPoint.getSignature();
        Method method = methodSignature.getMethod();
        Object target = joinPoint.getTarget();
        if (target == null) {
            return method;
        }
        return AopUtils.getMostSpecificMethod(method, target.getClass());
    }
}
