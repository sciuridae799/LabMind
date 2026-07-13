package com.labmind.redisson.delayqueue.core;

import com.labmind.redisson.delayqueue.event.ConsumerTask;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.context.ApplicationContext;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

public class DelayQueueInitHandler implements SmartInitializingSingleton, DisposableBean {

    private final ApplicationContext applicationContext;

    private final RedissonClient redissonClient;

    private final IsolationRegionSelector isolationRegionSelector;

    private final Duration consumerPollTimeout;

    private final List<DelayConsumerQueue> consumerQueues = new ArrayList<>();

    private ExecutorService executorService;

    public DelayQueueInitHandler(
            ApplicationContext applicationContext,
            RedissonClient redissonClient,
            IsolationRegionSelector isolationRegionSelector,
            Duration consumerPollTimeout) {
        Assert.notNull(applicationContext, "applicationContext must not be null");
        Assert.notNull(redissonClient, "redissonClient must not be null");
        Assert.notNull(isolationRegionSelector, "isolationRegionSelector must not be null");
        Assert.notNull(consumerPollTimeout, "consumerPollTimeout must not be null");
        this.applicationContext = applicationContext;
        this.redissonClient = redissonClient;
        this.isolationRegionSelector = isolationRegionSelector;
        this.consumerPollTimeout = consumerPollTimeout;
    }

    @Override
    public void afterSingletonsInstantiated() {
        Map<String, ConsumerTask> consumerTaskMap = applicationContext.getBeansOfType(ConsumerTask.class);
        if (consumerTaskMap.isEmpty()) {
            return;
        }

        validateUniqueTopics(consumerTaskMap.values());
        executorService = Executors.newFixedThreadPool(
                consumerTaskMap.size() * isolationRegionSelector.shardCount(),
                new DelayQueueConsumerThreadFactory());

        for (ConsumerTask consumerTask : consumerTaskMap.values()) {
            for (String shardTopic : isolationRegionSelector.listTopics(consumerTask.topic())) {
                DelayConsumerQueue delayConsumerQueue =
                        new DelayConsumerQueue(redissonClient, consumerTask, shardTopic, consumerPollTimeout);
                consumerQueues.add(delayConsumerQueue);
                executorService.submit(delayConsumerQueue);
            }
        }
    }

    @Override
    public void destroy() {
        for (DelayConsumerQueue consumerQueue : consumerQueues) {
            consumerQueue.stop();
        }
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }

    private void validateUniqueTopics(Iterable<ConsumerTask> consumerTasks) {
        Set<String> topics = new HashSet<>();
        for (ConsumerTask consumerTask : consumerTasks) {
            if (!StringUtils.hasText(consumerTask.topic())) {
                throw new IllegalStateException("ConsumerTask.topic must not be blank.");
            }
            String normalizedTopic = consumerTask.topic().trim();
            if (!topics.add(normalizedTopic)) {
                throw new IllegalStateException("Duplicate delay queue consumer topic registered: " + normalizedTopic);
            }
        }
    }

    private static final class DelayQueueConsumerThreadFactory implements ThreadFactory {

        private final AtomicInteger counter = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "delay-queue-consumer-" + counter.getAndIncrement());
            thread.setDaemon(false);
            return thread;
        }
    }
}
