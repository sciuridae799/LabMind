package com.labmind.redisson.repeatexecutelimit.lockinfo.impl;

import com.labmind.redisson.common.lockinfo.factory.LockInfoHandleFactory;
import com.labmind.redisson.repeatexecutelimit.annotation.RepeatExecuteLimit;
import com.labmind.redisson.repeatexecutelimit.constant.RepeatExecuteLimitConstant;
import com.labmind.redisson.repeatexecutelimit.core.RepeatExecuteLimitInfo;
import com.labmind.redisson.repeatexecutelimit.core.RepeatExecuteLimitKeyEvaluator;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.springframework.util.Assert;

public class RepeatExecuteLimitLockInfoHandle {

    private final LockInfoHandleFactory lockInfoHandleFactory;

    private final RepeatExecuteLimitKeyEvaluator repeatExecuteLimitKeyEvaluator;

    public RepeatExecuteLimitLockInfoHandle(
            LockInfoHandleFactory lockInfoHandleFactory,
            RepeatExecuteLimitKeyEvaluator repeatExecuteLimitKeyEvaluator) {
        Assert.notNull(lockInfoHandleFactory, "lockInfoHandleFactory must not be null");
        Assert.notNull(repeatExecuteLimitKeyEvaluator, "repeatExecuteLimitKeyEvaluator must not be null");
        this.lockInfoHandleFactory = lockInfoHandleFactory;
        this.repeatExecuteLimitKeyEvaluator = repeatExecuteLimitKeyEvaluator;
    }

    public RepeatExecuteLimitInfo create(Method method, Object[] arguments, RepeatExecuteLimit repeatExecuteLimit) {
        Assert.notNull(method, "method must not be null");
        Assert.notNull(repeatExecuteLimit, "repeatExecuteLimit must not be null");
        validate(repeatExecuteLimit.waitTime(), repeatExecuteLimit.lockLeaseTime(), repeatExecuteLimit.successLeaseTime(),
                repeatExecuteLimit.timeUnit());
        List<String> keys = repeatExecuteLimitKeyEvaluator.resolveKeys(method, arguments, repeatExecuteLimit.keys());
        String namespace = method.getDeclaringClass().getName() + "." + method.getName();
        return new RepeatExecuteLimitInfo(
                createLockName(RepeatExecuteLimitConstant.SUCCESS_PREFIX, namespace, keys),
                createLockName(RepeatExecuteLimitConstant.LOCAL_LOCK_PREFIX, namespace, keys),
                createLockName(RepeatExecuteLimitConstant.DISTRIBUTED_LOCK_PREFIX, namespace, keys),
                repeatExecuteLimit.waitTime(),
                repeatExecuteLimit.lockLeaseTime(),
                repeatExecuteLimit.successLeaseTime(),
                repeatExecuteLimit.timeUnit());
    }

    private String createLockName(String prefix, String namespace, List<String> keys) {
        List<String> nameSegments = new ArrayList<>(keys.size() + 1);
        nameSegments.add(namespace);
        nameSegments.addAll(keys);
        return lockInfoHandleFactory.createLockName(prefix, nameSegments);
    }

    private void validate(long waitTime, long lockLeaseTime, long successLeaseTime, TimeUnit timeUnit) {
        if (waitTime < 0) {
            throw new IllegalStateException("RepeatExecuteLimit.waitTime must be greater than or equal to 0.");
        }
        if (lockLeaseTime <= 0) {
            throw new IllegalStateException("RepeatExecuteLimit.lockLeaseTime must be greater than 0.");
        }
        if (successLeaseTime <= 0) {
            throw new IllegalStateException("RepeatExecuteLimit.successLeaseTime must be greater than 0.");
        }
        if (timeUnit == null) {
            throw new IllegalStateException("RepeatExecuteLimit.timeUnit must not be null.");
        }
    }
}
