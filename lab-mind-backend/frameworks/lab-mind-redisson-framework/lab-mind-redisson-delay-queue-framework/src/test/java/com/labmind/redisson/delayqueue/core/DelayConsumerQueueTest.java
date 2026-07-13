package com.labmind.redisson.delayqueue.core;

import com.labmind.redisson.delayqueue.event.ConsumerTask;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RedissonClient;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DelayConsumerQueueTest {

    @Test
    void shouldConsumeMessageAndStopGracefully() throws Exception {
        RedissonClient redissonClient = mock(RedissonClient.class);
        @SuppressWarnings("unchecked")
        RBlockingQueue<Object> blockingQueue = mock(RBlockingQueue.class);
        ConsumerTask consumerTask = mock(ConsumerTask.class);
        when(redissonClient.getBlockingQueue("chat-0")).thenReturn(blockingQueue);

        AtomicReference<DelayConsumerQueue> queueReference = new AtomicReference<>();
        when(blockingQueue.poll(anyLong(), eq(java.util.concurrent.TimeUnit.MILLISECONDS)))
                .thenReturn("message")
                .thenAnswer(invocation -> {
                    queueReference.get().stop();
                    throw new InterruptedException("stopped");
                });

        DelayConsumerQueue delayConsumerQueue =
                new DelayConsumerQueue(redissonClient, consumerTask, "chat-0", Duration.ofMillis(10));
        queueReference.set(delayConsumerQueue);

        delayConsumerQueue.run();

        verify(consumerTask).consume("message");
    }
}
