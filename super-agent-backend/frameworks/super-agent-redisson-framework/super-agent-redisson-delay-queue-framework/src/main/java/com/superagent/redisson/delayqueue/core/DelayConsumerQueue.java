package com.superagent.redisson.delayqueue.core;

import com.superagent.redisson.delayqueue.event.ConsumerTask;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RedissonClient;
import org.springframework.util.Assert;

public class DelayConsumerQueue implements Runnable {

    private final RedissonClient redissonClient;

    private final ConsumerTask consumerTask;

    private final String shardTopic;

    private final Duration consumerPollTimeout;

    private final AtomicBoolean running = new AtomicBoolean(true);

    public DelayConsumerQueue(
            RedissonClient redissonClient,
            ConsumerTask consumerTask,
            String shardTopic,
            Duration consumerPollTimeout) {
        Assert.notNull(redissonClient, "redissonClient must not be null");
        Assert.notNull(consumerTask, "consumerTask must not be null");
        Assert.hasText(shardTopic, "shardTopic must not be blank");
        Assert.notNull(consumerPollTimeout, "consumerPollTimeout must not be null");
        this.redissonClient = redissonClient;
        this.consumerTask = consumerTask;
        this.shardTopic = shardTopic.trim();
        this.consumerPollTimeout = consumerPollTimeout;
    }

    @Override
    public void run() {
        RBlockingQueue<Object> blockingQueue = redissonClient.getBlockingQueue(shardTopic);
        while (running.get()) {
            try {
                Object message = blockingQueue.poll(consumerPollTimeout.toMillis(), TimeUnit.MILLISECONDS);
                if (message != null) {
                    consumerTask.consume(message);
                }
            }
            catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                if (running.get()) {
                    throw new IllegalStateException("Delay queue consumer interrupted: " + shardTopic, ex);
                }
                return;
            }
            catch (Exception ex) {
                throw new IllegalStateException("Delay queue consumer failed for topic: " + shardTopic, ex);
            }
        }
    }

    public void stop() {
        running.set(false);
    }
}
