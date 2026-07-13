package com.labmind.redisson.servicelock.servicelock.factory;

import com.labmind.redisson.servicelock.core.LockType;
import com.labmind.redisson.servicelock.core.ManageLocker;
import com.labmind.redisson.servicelock.core.ServiceLocker;
import org.springframework.util.Assert;

public class ServiceLockFactory {

    private final ManageLocker manageLocker;

    public ServiceLockFactory(ManageLocker manageLocker) {
        Assert.notNull(manageLocker, "manageLocker must not be null");
        this.manageLocker = manageLocker;
    }

    public ServiceLocker getLocker(LockType lockType) {
        return manageLocker.getLocker(lockType);
    }
}
