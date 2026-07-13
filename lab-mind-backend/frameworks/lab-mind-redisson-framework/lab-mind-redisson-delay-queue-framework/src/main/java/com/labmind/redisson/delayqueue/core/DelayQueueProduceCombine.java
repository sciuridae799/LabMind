package com.labmind.redisson.delayqueue.core;

import java.util.concurrent.TimeUnit;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

public class DelayQueueProduceCombine {

    private final IsolationRegionSelector isolationRegionSelector;

    private final DelayProduceQueue delayProduceQueue;

    public DelayQueueProduceCombine(
            IsolationRegionSelector isolationRegionSelector,
            DelayProduceQueue delayProduceQueue) {
        Assert.notNull(isolationRegionSelector, "isolationRegionSelector must not be null");
        Assert.notNull(delayProduceQueue, "delayProduceQueue must not be null");
        this.isolationRegionSelector = isolationRegionSelector;
        this.delayProduceQueue = delayProduceQueue;
    }

    public void sendMessage(String topic, Object content, long delay, TimeUnit timeUnit) {
        if (!StringUtils.hasText(topic)) {
            throw new IllegalArgumentException("topic must not be blank");
        }
        Assert.notNull(content, "content must not be null");
        Assert.notNull(timeUnit, "timeUnit must not be null");
        if (delay < 0) {
            throw new IllegalArgumentException("delay must be greater than or equal to 0");
        }
        String shardTopic = isolationRegionSelector.selectTopic(topic);
        delayProduceQueue.send(shardTopic, content, delay, timeUnit);
    }
}
