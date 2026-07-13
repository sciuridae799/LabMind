package com.labmind.redisson.common.locallock;

import java.time.Duration;
import java.util.concurrent.locks.ReentrantLock;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LocalLockCacheTest {

    @Test
    void shouldEvictExpiredUnlockedLock() throws InterruptedException {
        LocalLockCache localLockCache = new LocalLockCache(Duration.ofMillis(20));
        localLockCache.getLock("order:1");

        Thread.sleep(40L);
        localLockCache.evictExpiredLocks();

        assertThat(localLockCache.size()).isZero();
    }

    @Test
    void shouldKeepLockedEntryUntilUnlocked() throws InterruptedException {
        LocalLockCache localLockCache = new LocalLockCache(Duration.ofMillis(20));
        ReentrantLock lock = localLockCache.getLock("order:2");
        lock.lock();

        Thread.sleep(40L);
        localLockCache.evictExpiredLocks();
        assertThat(localLockCache.size()).isEqualTo(1);

        lock.unlock();
        localLockCache.evictExpiredLocks();
        assertThat(localLockCache.size()).isZero();
    }
}
