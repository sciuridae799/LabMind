package com.superagent.redisson.repeatexecutelimit.lockinfo.impl;

import com.superagent.redisson.common.lockinfo.factory.LockInfoHandleFactory;
import com.superagent.redisson.repeatexecutelimit.annotation.RepeatExecuteLimit;
import com.superagent.redisson.repeatexecutelimit.core.RepeatExecuteLimitInfo;
import com.superagent.redisson.repeatexecutelimit.core.RepeatExecuteLimitKeyEvaluator;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RepeatExecuteLimitLockInfoHandleTest {

    private final RepeatExecuteLimitLockInfoHandle repeatExecuteLimitLockInfoHandle =
            new RepeatExecuteLimitLockInfoHandle(new LockInfoHandleFactory(), new RepeatExecuteLimitKeyEvaluator());

    @Test
    void shouldCreateRepeatExecuteLimitInfo() throws NoSuchMethodException {
        Method method = SampleService.class.getDeclaredMethod("submit", String.class);
        RepeatExecuteLimit repeatExecuteLimit = method.getAnnotation(RepeatExecuteLimit.class);

        RepeatExecuteLimitInfo info =
                repeatExecuteLimitLockInfoHandle.create(method, new Object[] {"42"}, repeatExecuteLimit);

        assertThat(info.successKey())
                .isEqualTo("repeat-execute-limit:success:" + SampleService.class.getName() + ".submit:42");
        assertThat(info.localLockKey())
                .isEqualTo("repeat-execute-limit:local-lock:" + SampleService.class.getName() + ".submit:42");
        assertThat(info.distributedLockName())
                .isEqualTo("repeat-execute-limit:distributed-lock:" + SampleService.class.getName() + ".submit:42");
        assertThat(info.waitTime()).isEqualTo(1L);
        assertThat(info.lockLeaseTime()).isEqualTo(5L);
        assertThat(info.successLeaseTime()).isEqualTo(10L);
        assertThat(info.timeUnit()).isEqualTo(TimeUnit.SECONDS);
    }

    private static final class SampleService {

        @RepeatExecuteLimit(
                keys = {"#p0"},
                waitTime = 1L,
                lockLeaseTime = 5L,
                successLeaseTime = 10L,
                timeUnit = TimeUnit.SECONDS)
        public void submit(String orderId) {
        }
    }
}
