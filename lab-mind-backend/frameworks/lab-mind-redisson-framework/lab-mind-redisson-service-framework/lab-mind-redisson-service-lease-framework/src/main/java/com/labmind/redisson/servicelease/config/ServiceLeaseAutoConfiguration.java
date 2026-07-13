package com.labmind.redisson.servicelease.config;

import com.labmind.redisson.common.config.RedissonCommonAutoConfiguration;
import com.labmind.redisson.servicelease.lease.RedisLeaseManager;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = RedissonCommonAutoConfiguration.class)
@ConditionalOnBean(RedissonClient.class)
public class ServiceLeaseAutoConfiguration {

    @Bean
    public RedisLeaseManager redisLeaseManager(RedissonClient redissonClient) {
        return new RedisLeaseManager(redissonClient);
    }
}
