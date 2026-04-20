package com.superagent.redisson.delayqueue.core;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class DelayQueueProduceCombineTest {

    @Test
    void shouldSendMessagesUsingSharedSelector() {
        IsolationRegionSelector isolationRegionSelector = new IsolationRegionSelector(2);
        DelayProduceQueue delayProduceQueue = mock(DelayProduceQueue.class);
        DelayQueueProduceCombine delayQueueProduceCombine =
                new DelayQueueProduceCombine(isolationRegionSelector, delayProduceQueue);

        delayQueueProduceCombine.sendMessage("chat", "m1", 1L, TimeUnit.SECONDS);
        delayQueueProduceCombine.sendMessage("chat", "m2", 1L, TimeUnit.SECONDS);

        ArgumentCaptor<String> topicCaptor = ArgumentCaptor.forClass(String.class);
        verify(delayProduceQueue).send(topicCaptor.capture(), eq("m1"), eq(1L), eq(TimeUnit.SECONDS));
        verify(delayProduceQueue).send(topicCaptor.capture(), eq("m2"), eq(1L), eq(TimeUnit.SECONDS));
        assertThat(topicCaptor.getAllValues()).containsExactly("chat-0", "chat-1");
    }
}
