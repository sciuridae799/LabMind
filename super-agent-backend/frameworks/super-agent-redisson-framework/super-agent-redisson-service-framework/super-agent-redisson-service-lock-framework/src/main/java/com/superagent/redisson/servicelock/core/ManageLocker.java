package com.superagent.redisson.servicelock.core;

import java.util.EnumMap;
import java.util.List;
import org.springframework.util.Assert;

public class ManageLocker {

    private final EnumMap<LockType, ServiceLocker> lockers = new EnumMap<>(LockType.class);

    public ManageLocker(List<ServiceLocker> serviceLockers) {
        Assert.notEmpty(serviceLockers, "serviceLockers must not be empty");
        for (ServiceLocker serviceLocker : serviceLockers) {
            ServiceLocker previous = lockers.put(serviceLocker.lockType(), serviceLocker);
            if (previous != null) {
                throw new IllegalStateException("Duplicate locker registered for lock type: " + serviceLocker.lockType());
            }
        }
        for (LockType lockType : LockType.values()) {
            if (!lockers.containsKey(lockType)) {
                throw new IllegalStateException("Missing locker implementation for lock type: " + lockType);
            }
        }
    }

    public ServiceLocker getLocker(LockType lockType) {
        Assert.notNull(lockType, "lockType must not be null");
        ServiceLocker serviceLocker = lockers.get(lockType);
        if (serviceLocker == null) {
            throw new IllegalStateException("No locker registered for lock type: " + lockType);
        }
        return serviceLocker;
    }
}
