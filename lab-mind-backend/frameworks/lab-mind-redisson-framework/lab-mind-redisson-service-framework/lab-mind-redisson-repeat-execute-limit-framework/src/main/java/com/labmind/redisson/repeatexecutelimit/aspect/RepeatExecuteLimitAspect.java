package com.labmind.redisson.repeatexecutelimit.aspect;

import com.labmind.redisson.common.handler.RedissonDataHandle;
import com.labmind.redisson.common.locallock.LocalLockCache;
import com.labmind.redisson.repeatexecutelimit.annotation.RepeatExecuteLimit;
import com.labmind.redisson.repeatexecutelimit.constant.RepeatExecuteLimitConstant;
import com.labmind.redisson.repeatexecutelimit.core.RepeatExecuteLimitInfo;
import com.labmind.redisson.repeatexecutelimit.lockinfo.impl.RepeatExecuteLimitLockInfoHandle;
import com.labmind.redisson.servicelock.core.LockType;
import com.labmind.redisson.servicelock.core.ServiceLocker;
import com.labmind.redisson.servicelock.servicelock.factory.ServiceLockFactory;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.concurrent.locks.ReentrantLock;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.aop.support.AopUtils;
import org.springframework.util.Assert;

@Aspect
public class RepeatExecuteLimitAspect {

    private final RedissonDataHandle redissonDataHandle;

    private final LocalLockCache localLockCache;

    private final ServiceLockFactory serviceLockFactory;

    private final RepeatExecuteLimitLockInfoHandle repeatExecuteLimitLockInfoHandle;

    public RepeatExecuteLimitAspect(
            RedissonDataHandle redissonDataHandle,
            LocalLockCache localLockCache,
            ServiceLockFactory serviceLockFactory,
            RepeatExecuteLimitLockInfoHandle repeatExecuteLimitLockInfoHandle) {
        Assert.notNull(redissonDataHandle, "redissonDataHandle must not be null");
        Assert.notNull(localLockCache, "localLockCache must not be null");
        Assert.notNull(serviceLockFactory, "serviceLockFactory must not be null");
        Assert.notNull(repeatExecuteLimitLockInfoHandle, "repeatExecuteLimitLockInfoHandle must not be null");
        this.redissonDataHandle = redissonDataHandle;
        this.localLockCache = localLockCache;
        this.serviceLockFactory = serviceLockFactory;
        this.repeatExecuteLimitLockInfoHandle = repeatExecuteLimitLockInfoHandle;
    }

    @Around("@annotation(repeatExecuteLimit)")
    public Object around(ProceedingJoinPoint joinPoint, RepeatExecuteLimit repeatExecuteLimit) throws Throwable {
        Method method = resolveMethod(joinPoint);
        RepeatExecuteLimitInfo repeatExecuteLimitInfo =
                repeatExecuteLimitLockInfoHandle.create(method, joinPoint.getArgs(), repeatExecuteLimit);

        rejectIfMarkedSuccess(repeatExecuteLimitInfo.successKey());

        ReentrantLock localLock = localLockCache.getLock(repeatExecuteLimitInfo.localLockKey());
        if (!localLock.tryLock()) {
            throw new IllegalStateException(
                    "Repeat execute blocked by local lock: " + repeatExecuteLimitInfo.localLockKey());
        }

        ServiceLocker serviceLocker = serviceLockFactory.getLocker(LockType.Fair);
        boolean distributedLocked = false;
        try {
            distributedLocked = serviceLocker.tryLock(
                    repeatExecuteLimitInfo.distributedLockName(),
                    repeatExecuteLimitInfo.waitTime(),
                    repeatExecuteLimitInfo.lockLeaseTime(),
                    repeatExecuteLimitInfo.timeUnit());
            if (!distributedLocked) {
                throw new IllegalStateException(
                        "Repeat execute blocked by distributed lock: "
                                + repeatExecuteLimitInfo.distributedLockName());
            }

            Object result = joinPoint.proceed();
            redissonDataHandle.set(
                    repeatExecuteLimitInfo.successKey(),
                    RepeatExecuteLimitConstant.SUCCESS_FLAG,
                    Duration.ofNanos(repeatExecuteLimitInfo.timeUnit().toNanos(repeatExecuteLimitInfo.successLeaseTime())));
            return result;
        }
        finally {
            if (distributedLocked) {
                serviceLocker.unlock(repeatExecuteLimitInfo.distributedLockName());
            }
            if (localLock.isHeldByCurrentThread()) {
                localLock.unlock();
            }
            localLockCache.remove(repeatExecuteLimitInfo.localLockKey());
        }
    }

    private void rejectIfMarkedSuccess(String successKey) {
        Object successMarker = redissonDataHandle.get(successKey);
        if (successMarker == null) {
            return;
        }
        if (!RepeatExecuteLimitConstant.SUCCESS_FLAG.equals(successMarker)) {
            throw new IllegalStateException("Unexpected repeat execute marker value for key: " + successKey);
        }
        throw new IllegalStateException("Repeat execute blocked by success marker: " + successKey);
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
