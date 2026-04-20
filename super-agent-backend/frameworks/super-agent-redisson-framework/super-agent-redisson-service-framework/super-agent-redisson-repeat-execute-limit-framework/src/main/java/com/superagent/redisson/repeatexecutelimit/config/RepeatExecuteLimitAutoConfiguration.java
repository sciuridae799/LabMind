package com.superagent.redisson.repeatexecutelimit.config;

import com.superagent.redisson.common.config.RedissonCommonAutoConfiguration;
import com.superagent.redisson.common.handler.RedissonDataHandle;
import com.superagent.redisson.common.locallock.LocalLockCache;
import com.superagent.redisson.common.lockinfo.factory.LockInfoHandleFactory;
import com.superagent.redisson.repeatexecutelimit.aspect.RepeatExecuteLimitAspect;
import com.superagent.redisson.repeatexecutelimit.core.RepeatExecuteLimitKeyEvaluator;
import com.superagent.redisson.repeatexecutelimit.lockinfo.impl.RepeatExecuteLimitLockInfoHandle;
import com.superagent.redisson.servicelock.config.ServiceLockAutoConfiguration;
import com.superagent.redisson.servicelock.servicelock.factory.ServiceLockFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = {RedissonCommonAutoConfiguration.class, ServiceLockAutoConfiguration.class})
@ConditionalOnBean({
        RedissonDataHandle.class,
        LocalLockCache.class,
        ServiceLockFactory.class,
        LockInfoHandleFactory.class
})
public class RepeatExecuteLimitAutoConfiguration {

    @Bean
    public RepeatExecuteLimitKeyEvaluator repeatExecuteLimitKeyEvaluator() {
        return new RepeatExecuteLimitKeyEvaluator();
    }

    @Bean
    public RepeatExecuteLimitLockInfoHandle repeatExecuteLimitLockInfoHandle(
            LockInfoHandleFactory lockInfoHandleFactory,
            RepeatExecuteLimitKeyEvaluator repeatExecuteLimitKeyEvaluator) {
        return new RepeatExecuteLimitLockInfoHandle(lockInfoHandleFactory, repeatExecuteLimitKeyEvaluator);
    }

    @Bean
    public RepeatExecuteLimitAspect repeatExecuteLimitAspect(
            RedissonDataHandle redissonDataHandle,
            LocalLockCache localLockCache,
            ServiceLockFactory serviceLockFactory,
            RepeatExecuteLimitLockInfoHandle repeatExecuteLimitLockInfoHandle) {
        return new RepeatExecuteLimitAspect(
                redissonDataHandle,
                localLockCache,
                serviceLockFactory,
                repeatExecuteLimitLockInfoHandle);
    }
}
