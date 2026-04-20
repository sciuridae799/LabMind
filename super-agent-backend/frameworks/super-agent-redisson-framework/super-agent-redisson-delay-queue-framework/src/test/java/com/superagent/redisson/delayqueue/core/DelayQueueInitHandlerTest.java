package com.superagent.redisson.delayqueue.core;

import com.superagent.redisson.delayqueue.event.ConsumerTask;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DelayQueueInitHandlerTest {

    @Test
    void shouldIgnoreEmptyConsumerRegistry() {
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        when(applicationContext.getBeansOfType(ConsumerTask.class)).thenReturn(Map.of());

        DelayQueueInitHandler delayQueueInitHandler = new DelayQueueInitHandler(
                applicationContext,
                mock(RedissonClient.class),
                new IsolationRegionSelector(2),
                Duration.ofSeconds(1));

        assertThatCode(delayQueueInitHandler::afterSingletonsInstantiated).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectDuplicateTopics() {
        ApplicationContext applicationContext = mock(ApplicationContext.class);
        ConsumerTask consumerTask1 = new StaticConsumerTask("chat");
        ConsumerTask consumerTask2 = new StaticConsumerTask("chat");
        when(applicationContext.getBeansOfType(ConsumerTask.class))
                .thenReturn(Map.of("c1", consumerTask1, "c2", consumerTask2));

        DelayQueueInitHandler delayQueueInitHandler = new DelayQueueInitHandler(
                applicationContext,
                mock(RedissonClient.class),
                new IsolationRegionSelector(2),
                Duration.ofSeconds(1));

        assertThatThrownBy(delayQueueInitHandler::afterSingletonsInstantiated)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate delay queue consumer topic");
    }

    private record StaticConsumerTask(String topic) implements ConsumerTask {

        @Override
        public void consume(Object content) {
        }
    }
}
