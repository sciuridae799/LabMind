package com.superagent.redisson.repeatexecutelimit.aspect;

import com.superagent.redisson.common.handler.RedissonDataHandle;
import com.superagent.redisson.common.locallock.LocalLockCache;
import com.superagent.redisson.common.lockinfo.factory.LockInfoHandleFactory;
import com.superagent.redisson.repeatexecutelimit.annotation.RepeatExecuteLimit;
import com.superagent.redisson.repeatexecutelimit.constant.RepeatExecuteLimitConstant;
import com.superagent.redisson.repeatexecutelimit.core.RepeatExecuteLimitKeyEvaluator;
import com.superagent.redisson.repeatexecutelimit.lockinfo.impl.RepeatExecuteLimitLockInfoHandle;
import com.superagent.redisson.servicelock.core.LockType;
import com.superagent.redisson.servicelock.core.ServiceLocker;
import com.superagent.redisson.servicelock.servicelock.factory.ServiceLockFactory;
import java.lang.reflect.Method;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RepeatExecuteLimitAspectTest {

    private final RepeatExecuteLimitLockInfoHandle repeatExecuteLimitLockInfoHandle =
            new RepeatExecuteLimitLockInfoHandle(new LockInfoHandleFactory(), new RepeatExecuteLimitKeyEvaluator());

    @Test
    void shouldWriteSuccessMarkerAfterBusinessSuccess() throws Throwable {
        RedissonDataHandle redissonDataHandle = mock(RedissonDataHandle.class);
        LocalLockCache localLockCache = new LocalLockCache(Duration.ofSeconds(30));
        ServiceLockFactory serviceLockFactory = mock(ServiceLockFactory.class);
        ServiceLocker serviceLocker = mock(ServiceLocker.class);
        ProceedingJoinPoint joinPoint = mockJoinPoint();

        when(redissonDataHandle.get(any())).thenReturn(null);
        when(serviceLockFactory.getLocker(LockType.Fair)).thenReturn(serviceLocker);
        when(serviceLocker.tryLock(any(), eq(0L), eq(5L), eq(TimeUnit.SECONDS))).thenReturn(true);
        when(joinPoint.proceed()).thenReturn("ok");

        RepeatExecuteLimitAspect repeatExecuteLimitAspect =
                new RepeatExecuteLimitAspect(
                        redissonDataHandle,
                        localLockCache,
                        serviceLockFactory,
                        repeatExecuteLimitLockInfoHandle);
        repeatExecuteLimitAspect.around(joinPoint, repeatAnnotation());

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Duration> durationCaptor = ArgumentCaptor.forClass(Duration.class);
        verify(redissonDataHandle).set(
                keyCaptor.capture(),
                eq(RepeatExecuteLimitConstant.SUCCESS_FLAG),
                durationCaptor.capture());
        verify(serviceLocker).unlock("repeat-execute-limit:distributed-lock:" + SampleService.class.getName() + ".submit:42");
    }

    @Test
    void shouldNotWriteSuccessMarkerWhenBusinessFails() throws Throwable {
        RedissonDataHandle redissonDataHandle = mock(RedissonDataHandle.class);
        LocalLockCache localLockCache = new LocalLockCache(Duration.ofSeconds(30));
        ServiceLockFactory serviceLockFactory = mock(ServiceLockFactory.class);
        ServiceLocker serviceLocker = mock(ServiceLocker.class);
        ProceedingJoinPoint joinPoint = mockJoinPoint();

        when(redissonDataHandle.get(any())).thenReturn(null);
        when(serviceLockFactory.getLocker(LockType.Fair)).thenReturn(serviceLocker);
        when(serviceLocker.tryLock(any(), eq(0L), eq(5L), eq(TimeUnit.SECONDS))).thenReturn(true);
        when(joinPoint.proceed()).thenThrow(new IllegalStateException("biz failed"));

        RepeatExecuteLimitAspect repeatExecuteLimitAspect =
                new RepeatExecuteLimitAspect(
                        redissonDataHandle,
                        localLockCache,
                        serviceLockFactory,
                        repeatExecuteLimitLockInfoHandle);

        assertThatThrownBy(() -> repeatExecuteLimitAspect.around(joinPoint, repeatAnnotation()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("biz failed");
        verify(redissonDataHandle, never()).set(any(), any(), any(Duration.class));
        verify(serviceLocker).unlock("repeat-execute-limit:distributed-lock:" + SampleService.class.getName() + ".submit:42");
    }

    @Test
    void shouldRejectRequestWhenSuccessMarkerExists() throws Throwable {
        RedissonDataHandle redissonDataHandle = mock(RedissonDataHandle.class);
        LocalLockCache localLockCache = mock(LocalLockCache.class);
        ServiceLockFactory serviceLockFactory = mock(ServiceLockFactory.class);
        ProceedingJoinPoint joinPoint = mockJoinPoint();

        when(redissonDataHandle.get(any())).thenReturn(RepeatExecuteLimitConstant.SUCCESS_FLAG);

        RepeatExecuteLimitAspect repeatExecuteLimitAspect =
                new RepeatExecuteLimitAspect(
                        redissonDataHandle,
                        localLockCache,
                        serviceLockFactory,
                        repeatExecuteLimitLockInfoHandle);

        assertThatThrownBy(() -> repeatExecuteLimitAspect.around(joinPoint, repeatAnnotation()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("success marker");
        verify(serviceLockFactory, never()).getLocker(any());
    }

    private ProceedingJoinPoint mockJoinPoint() throws NoSuchMethodException {
        ProceedingJoinPoint joinPoint = mock(ProceedingJoinPoint.class);
        MethodSignature methodSignature = mock(MethodSignature.class);
        Method method = SampleService.class.getDeclaredMethod("submit", String.class);
        when(joinPoint.getSignature()).thenReturn(methodSignature);
        when(methodSignature.getMethod()).thenReturn(method);
        when(joinPoint.getTarget()).thenReturn(new SampleService());
        when(joinPoint.getArgs()).thenReturn(new Object[] {"42"});
        return joinPoint;
    }

    private RepeatExecuteLimit repeatAnnotation() throws NoSuchMethodException {
        return SampleService.class.getDeclaredMethod("submit", String.class).getAnnotation(RepeatExecuteLimit.class);
    }

    private static final class SampleService {

        @RepeatExecuteLimit(keys = {"#p0"}, lockLeaseTime = 5L, successLeaseTime = 10L, timeUnit = TimeUnit.SECONDS)
        public void submit(String orderId) {
        }
    }
}
