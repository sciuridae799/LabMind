package com.labmind.redisson.delayqueue.config;

import com.labmind.redisson.common.config.RedissonCommonAutoConfiguration;
import com.labmind.redisson.delayqueue.context.DelayQueueContext;
import com.labmind.redisson.delayqueue.core.DelayConsumerQueue;
import com.labmind.redisson.delayqueue.core.DelayProduceQueue;
import com.labmind.redisson.delayqueue.core.DelayQueueInitHandler;
import com.labmind.redisson.delayqueue.core.DelayQueueProduceCombine;
import com.labmind.redisson.delayqueue.core.IsolationRegionSelector;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = RedissonCommonAutoConfiguration.class)
@EnableConfigurationProperties(DelayQueueProperties.class)
@ConditionalOnBean(RedissonClient.class)
@ConditionalOnProperty(
        prefix = "spring.redis.redisson.delay-queue",
        name = {"shard-count", "consumer-poll-timeout"})
public class DelayQueueAutoConfig {

    @Bean
    public IsolationRegionSelector isolationRegionSelector(DelayQueueProperties delayQueueProperties) {
        return new IsolationRegionSelector(requireShardCount(delayQueueProperties));
    }

    @Bean
    public DelayProduceQueue delayProduceQueue(RedissonClient redissonClient) {
        return new DelayProduceQueue(redissonClient);
    }

    @Bean
    public DelayQueueProduceCombine delayQueueProduceCombine(
            IsolationRegionSelector isolationRegionSelector,
            DelayProduceQueue delayProduceQueue) {
        return new DelayQueueProduceCombine(isolationRegionSelector, delayProduceQueue);
    }

    @Bean
    public DelayQueueContext delayQueueContext(DelayQueueProduceCombine delayQueueProduceCombine) {
        return new DelayQueueContext(delayQueueProduceCombine);
    }

    @Bean
    public DelayQueueInitHandler delayQueueInitHandler(
            ApplicationContext applicationContext,
            RedissonClient redissonClient,
            IsolationRegionSelector isolationRegionSelector,
            DelayQueueProperties delayQueueProperties) {
        return new DelayQueueInitHandler(
                applicationContext,
                redissonClient,
                isolationRegionSelector,
                requireConsumerPollTimeout(delayQueueProperties));
    }

    private int requireShardCount(DelayQueueProperties delayQueueProperties) {
        Integer shardCount = delayQueueProperties.getShardCount();
        if (shardCount == null || shardCount <= 0) {
            throw new IllegalStateException("spring.redis.redisson.delay-queue.shard-count must be greater than 0.");
        }
        return shardCount;
    }

    private java.time.Duration requireConsumerPollTimeout(DelayQueueProperties delayQueueProperties) {
        java.time.Duration consumerPollTimeout = delayQueueProperties.getConsumerPollTimeout();
        if (consumerPollTimeout == null) {
            throw new IllegalStateException(
                    "spring.redis.redisson.delay-queue.consumer-poll-timeout must be configured.");
        }
        if (consumerPollTimeout.isZero() || consumerPollTimeout.isNegative()) {
            throw new IllegalStateException(
                    "spring.redis.redisson.delay-queue.consumer-poll-timeout must be greater than 0.");
        }
        return consumerPollTimeout;
    }
}
