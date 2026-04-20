package com.superagent.redisson.servicelock.servicelock.aspect;

import com.superagent.redisson.servicelock.core.LockType;
import com.superagent.redisson.servicelock.core.ServiceLocker;
import com.superagent.redisson.servicelock.lockinfo.impl.ServiceLockInfoHandle;
import com.superagent.redisson.servicelock.servicelock.annotation.ServiceLock;
import com.superagent.redisson.servicelock.servicelock.factory.ServiceLockFactory;
import com.superagent.redisson.servicelock.servicelock.info.ServiceLockInfo;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ServiceLockAspectTest {

    @Test
    void shouldProceedAndUnlockWhenLockAcquired() throws Throwable {
        ServiceLockInfoHandle serviceLockInfoHandle = mock(ServiceLockInfoHandle.class);
        ServiceLockFactory serviceLockFactory = mock(ServiceLockFactory.class);
        ServiceLocker serviceLocker = mock(ServiceLocker.class);
        ProceedingJoinPoint joinPoint = mockJoinPoint();
        ServiceLock serviceLock = lockAnnotation();

        when(serviceLockInfoHandle.create(Mockito.any(Method.class), Mockito.any(Object[].class), Mockito.eq(serviceLock)))
                .thenReturn(new ServiceLockInfo("service-lock:test", LockType.Reentrant, 0L, 1L, TimeUnit.SECONDS));
        when(serviceLockFactory.getLocker(LockType.Reentrant)).thenReturn(serviceLocker);
        when(serviceLocker.tryLock("service-lock:test", 0L, 1L, TimeUnit.SECONDS)).thenReturn(true);
        when(joinPoint.proceed()).thenReturn("ok");

        ServiceLockAspect serviceLockAspect = new ServiceLockAspect(serviceLockInfoHandle, serviceLockFactory);
        Object result = serviceLockAspect.around(joinPoint, serviceLock);

        assertThat(result).isEqualTo("ok");
        verify(serviceLocker).unlock("service-lock:test");
    }

    @Test
    void shouldFailWhenLockCannotBeAcquired() throws Throwable {
        ServiceLockInfoHandle serviceLockInfoHandle = mock(ServiceLockInfoHandle.class);
        ServiceLockFactory serviceLockFactory = mock(ServiceLockFactory.class);
        ServiceLocker serviceLocker = mock(ServiceLocker.class);
        ProceedingJoinPoint joinPoint = mockJoinPoint();
        ServiceLock serviceLock = lockAnnotation();

        when(serviceLockInfoHandle.create(Mockito.any(Method.class), Mockito.any(Object[].class), Mockito.eq(serviceLock)))
                .thenReturn(new ServiceLockInfo("service-lock:test", LockType.Reentrant, 0L, 1L, TimeUnit.SECONDS));
        when(serviceLockFactory.getLocker(LockType.Reentrant)).thenReturn(serviceLocker);
        when(serviceLocker.tryLock("service-lock:test", 0L, 1L, TimeUnit.SECONDS)).thenReturn(false);

        ServiceLockAspect serviceLockAspect = new ServiceLockAspect(serviceLockInfoHandle, serviceLockFactory);

        assertThatThrownBy(() -> serviceLockAspect.around(joinPoint, serviceLock))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to acquire service lock");
        verify(serviceLocker, never()).unlock("service-lock:test");
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

    private ServiceLock lockAnnotation() throws NoSuchMethodException {
        return SampleService.class.getDeclaredMethod("submit", String.class).getAnnotation(ServiceLock.class);
    }

    private static final class SampleService {

        @ServiceLock(keys = {"#p0"}, leaseTime = 1L)
        public void submit(String orderId) {
        }
    }
}
