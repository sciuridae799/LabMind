package com.superagent.redisson.servicelock.core;

import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ManageLockerTest {

    @Test
    void shouldReturnLockerByType() {
        ManageLocker manageLocker = new ManageLocker(List.of(
                new StubServiceLocker(LockType.Reentrant),
                new StubServiceLocker(LockType.Fair),
                new StubServiceLocker(LockType.Read),
                new StubServiceLocker(LockType.Write)));

        assertThat(manageLocker.getLocker(LockType.Fair).lockType()).isEqualTo(LockType.Fair);
    }

    @Test
    void shouldRejectMissingLockerType() {
        assertThatThrownBy(() -> new ManageLocker(List.of(
                new StubServiceLocker(LockType.Reentrant),
                new StubServiceLocker(LockType.Fair),
                new StubServiceLocker(LockType.Read))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Missing locker implementation");
    }

    private record StubServiceLocker(LockType lockType) implements ServiceLocker {

        @Override
        public boolean tryLock(String lockName, long waitTime, long leaseTime, TimeUnit timeUnit) {
            return true;
        }

        @Override
        public void unlock(String lockName) {
        }
    }
}
