package com.superagent.redisson.servicelock.servicelock.factory;

import com.superagent.redisson.servicelock.core.LockType;
import com.superagent.redisson.servicelock.core.ManageLocker;
import com.superagent.redisson.servicelock.core.ServiceLocker;
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
