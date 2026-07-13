package com.labmind.redisson.delayqueue.context;

import com.labmind.redisson.delayqueue.core.DelayQueueProduceCombine;
import java.util.concurrent.TimeUnit;
import org.springframework.util.Assert;

public class DelayQueueContext {

    private final DelayQueueProduceCombine delayQueueProduceCombine;

    public DelayQueueContext(DelayQueueProduceCombine delayQueueProduceCombine) {
        Assert.notNull(delayQueueProduceCombine, "delayQueueProduceCombine must not be null");
        this.delayQueueProduceCombine = delayQueueProduceCombine;
    }

    public void sendMessage(String topic, Object content, long delay, TimeUnit timeUnit) {
        delayQueueProduceCombine.sendMessage(topic, content, delay, timeUnit);
    }
}
