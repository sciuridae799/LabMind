package com.labmind.redisson.delayqueue.core;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RedissonClient;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DelayProduceQueueTest {

    @Test
    void shouldOfferMessageIntoDelayedQueue() {
        RedissonClient redissonClient = mock(RedissonClient.class);
        @SuppressWarnings("unchecked")
        RBlockingQueue<Object> blockingQueue = mock(RBlockingQueue.class);
        @SuppressWarnings("unchecked")
        RDelayedQueue<Object> delayedQueue = mock(RDelayedQueue.class);
        when(redissonClient.getBlockingQueue("chat-0")).thenReturn(blockingQueue);
        when(redissonClient.getDelayedQueue(blockingQueue)).thenReturn(delayedQueue);

        DelayProduceQueue delayProduceQueue = new DelayProduceQueue(redissonClient);
        delayProduceQueue.send("chat-0", "message", 3L, TimeUnit.SECONDS);

        verify(delayedQueue).offer(eq("message"), eq(3L), eq(TimeUnit.SECONDS));
    }
}
