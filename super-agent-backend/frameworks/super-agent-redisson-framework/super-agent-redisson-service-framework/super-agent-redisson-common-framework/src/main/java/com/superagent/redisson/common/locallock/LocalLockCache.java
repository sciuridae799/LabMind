package com.superagent.redisson.common.locallock;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

public class LocalLockCache {

    private final long ttlNanos;

    private final ConcurrentHashMap<String, CachedLock> cachedLocks = new ConcurrentHashMap<>();

    public LocalLockCache(Duration ttl) {
        Assert.notNull(ttl, "ttl must not be null");
        if (ttl.isZero() || ttl.isNegative()) {
            throw new IllegalArgumentException("ttl must be greater than 0");
        }
        this.ttlNanos = ttl.toNanos();
    }

    public ReentrantLock getLock(String lockName) {
        validateLockName(lockName);
        evictExpiredLocks();
        CachedLock cachedLock = cachedLocks.compute(lockName.trim(), (key, existing) -> {
            if (existing == null) {
                return new CachedLock();
            }
            existing.touch();
            return existing;
        });
        cachedLock.touch();
        return cachedLock.lock();
    }

    public void remove(String lockName) {
        validateLockName(lockName);
        cachedLocks.computeIfPresent(lockName.trim(), (key, cachedLock) -> cachedLock.canEvict() ? null : cachedLock);
    }

    void evictExpiredLocks() {
        long now = System.nanoTime();
        cachedLocks.entrySet().removeIf(entry -> entry.getValue().shouldEvict(now, ttlNanos));
    }

    int size() {
        return cachedLocks.size();
    }

    private void validateLockName(String lockName) {
        if (!StringUtils.hasText(lockName)) {
            throw new IllegalArgumentException("lockName must not be blank");
        }
    }

    private static final class CachedLock {

        private final ReentrantLock lock = new ReentrantLock();

        private volatile long lastAccessNanos = System.nanoTime();

        private ReentrantLock lock() {
            return lock;
        }

        private void touch() {
            lastAccessNanos = System.nanoTime();
        }

        private boolean canEvict() {
            return !lock.isLocked() && !lock.hasQueuedThreads();
        }

        private boolean shouldEvict(long now, long ttlNanos) {
            return canEvict() && now - lastAccessNanos >= ttlNanos;
        }
    }
}
