package com.superagent.redisson.servicelock.lockinfo.impl;

import com.superagent.redisson.common.lockinfo.factory.LockInfoHandleFactory;
import com.superagent.redisson.servicelock.core.LockType;
import com.superagent.redisson.servicelock.servicelock.annotation.ServiceLock;
import com.superagent.redisson.servicelock.servicelock.info.ServiceLockInfo;
import com.superagent.redisson.servicelock.util.ServiceLockKeyEvaluator;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ServiceLockInfoHandleTest {

    private final ServiceLockInfoHandle serviceLockInfoHandle =
            new ServiceLockInfoHandle(new LockInfoHandleFactory(), new ServiceLockKeyEvaluator());

    @Test
    void shouldCreateServiceLockInfo() throws NoSuchMethodException {
        Method method = SampleService.class.getDeclaredMethod("submit", String.class);
        ServiceLock serviceLock = method.getAnnotation(ServiceLock.class);

        ServiceLockInfo serviceLockInfo = serviceLockInfoHandle.create(method, new Object[] {"42"}, serviceLock);

        assertThat(serviceLockInfo.lockName())
                .isEqualTo(SampleService.class.getName() + ".submit:42:fixed-key");
        assertThat(serviceLockInfo.lockType()).isEqualTo(LockType.Fair);
        assertThat(serviceLockInfo.waitTime()).isEqualTo(2L);
        assertThat(serviceLockInfo.leaseTime()).isEqualTo(5L);
        assertThat(serviceLockInfo.timeUnit()).isEqualTo(TimeUnit.SECONDS);
    }

    @Test
    void shouldRejectBlankKeyExpression() throws NoSuchMethodException {
        Method method = InvalidService.class.getDeclaredMethod("submit", String.class);
        ServiceLock serviceLock = method.getAnnotation(ServiceLock.class);

        assertThatThrownBy(() -> serviceLockInfoHandle.create(method, new Object[] {"42"}, serviceLock))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("blank expression");
    }

    private static final class SampleService {

        @ServiceLock(
                keys = {"#p0", "fixed-key"},
                lockType = LockType.Fair,
                waitTime = 2L,
                leaseTime = 5L,
                timeUnit = TimeUnit.SECONDS)
        public void submit(String orderId) {
        }
    }

    private static final class InvalidService {

        @ServiceLock(keys = {" "}, leaseTime = 1L)
        public void submit(String orderId) {
        }
    }
}
