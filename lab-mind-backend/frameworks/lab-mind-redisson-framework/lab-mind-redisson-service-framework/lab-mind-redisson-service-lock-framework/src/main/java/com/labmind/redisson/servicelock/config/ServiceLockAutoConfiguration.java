package com.labmind.redisson.servicelock.config;

import com.labmind.redisson.common.config.RedissonCommonAutoConfiguration;
import com.labmind.redisson.common.lockinfo.factory.LockInfoHandleFactory;
import com.labmind.redisson.servicelock.core.ManageLocker;
import com.labmind.redisson.servicelock.core.ServiceLocker;
import com.labmind.redisson.servicelock.lockinfo.impl.ServiceLockInfoHandle;
import com.labmind.redisson.servicelock.servicelock.aspect.ServiceLockAspect;
import com.labmind.redisson.servicelock.servicelock.factory.ServiceLockFactory;
import com.labmind.redisson.servicelock.servicelock.impl.RedissonFairLocker;
import com.labmind.redisson.servicelock.servicelock.impl.RedissonReadLocker;
import com.labmind.redisson.servicelock.servicelock.impl.RedissonReentrantLocker;
import com.labmind.redisson.servicelock.servicelock.impl.RedissonWriteLocker;
import com.labmind.redisson.servicelock.util.ServiceLockKeyEvaluator;
import java.util.List;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = RedissonCommonAutoConfiguration.class)
@ConditionalOnBean({RedissonClient.class, LockInfoHandleFactory.class})
public class ServiceLockAutoConfiguration {

    @Bean
    public ServiceLockKeyEvaluator serviceLockKeyEvaluator() {
        return new ServiceLockKeyEvaluator();
    }

    @Bean
    public ServiceLockInfoHandle serviceLockInfoHandle(
            LockInfoHandleFactory lockInfoHandleFactory,
            ServiceLockKeyEvaluator serviceLockKeyEvaluator) {
        return new ServiceLockInfoHandle(lockInfoHandleFactory, serviceLockKeyEvaluator);
    }

    @Bean
    public RedissonReentrantLocker redissonReentrantLocker(RedissonClient redissonClient) {
        return new RedissonReentrantLocker(redissonClient);
    }

    @Bean
    public RedissonFairLocker redissonFairLocker(RedissonClient redissonClient) {
        return new RedissonFairLocker(redissonClient);
    }

    @Bean
    public RedissonReadLocker redissonReadLocker(RedissonClient redissonClient) {
        return new RedissonReadLocker(redissonClient);
    }

    @Bean
    public RedissonWriteLocker redissonWriteLocker(RedissonClient redissonClient) {
        return new RedissonWriteLocker(redissonClient);
    }

    @Bean
    public ManageLocker manageLocker(List<ServiceLocker> serviceLockers) {
        return new ManageLocker(serviceLockers);
    }

    @Bean
    public ServiceLockFactory serviceLockFactory(ManageLocker manageLocker) {
        return new ServiceLockFactory(manageLocker);
    }

    @Bean
    public ServiceLockAspect serviceLockAspect(
            ServiceLockInfoHandle serviceLockInfoHandle,
            ServiceLockFactory serviceLockFactory) {
        return new ServiceLockAspect(serviceLockInfoHandle, serviceLockFactory);
    }
}
