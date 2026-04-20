package com.superagent.redisson.delayqueue.core;

import java.util.concurrent.TimeUnit;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RedissonClient;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

public class DelayProduceQueue {

    private final RedissonClient redissonClient;

    public DelayProduceQueue(RedissonClient redissonClient) {
        Assert.notNull(redissonClient, "redissonClient must not be null");
        this.redissonClient = redissonClient;
    }

    public void send(String shardTopic, Object content, long delay, TimeUnit timeUnit) {
        if (!StringUtils.hasText(shardTopic)) {
            throw new IllegalArgumentException("shardTopic must not be blank");
        }
        Assert.notNull(content, "content must not be null");
        Assert.notNull(timeUnit, "timeUnit must not be null");
        if (delay < 0) {
            throw new IllegalArgumentException("delay must be greater than or equal to 0");
        }

        RBlockingQueue<Object> blockingQueue = redissonClient.getBlockingQueue(shardTopic.trim());
        RDelayedQueue<Object> delayedQueue = redissonClient.getDelayedQueue(blockingQueue);
        delayedQueue.offer(content, delay, timeUnit);
    }
}
