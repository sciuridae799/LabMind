package com.superagent.redisson.delayqueue.core;

import java.util.List;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IsolationRegionSelectorTest {

    @Test
    void shouldRouteTopicsRoundRobin() {
        IsolationRegionSelector isolationRegionSelector = new IsolationRegionSelector(2);

        assertThat(isolationRegionSelector.selectTopic("chat")).isEqualTo("chat-0");
        assertThat(isolationRegionSelector.selectTopic("chat")).isEqualTo("chat-1");
        assertThat(isolationRegionSelector.selectTopic("chat")).isEqualTo("chat-0");
    }

    @Test
    void shouldListAllShardTopics() {
        IsolationRegionSelector isolationRegionSelector = new IsolationRegionSelector(3);

        assertThat(isolationRegionSelector.listTopics("chat"))
                .isEqualTo(List.of("chat-0", "chat-1", "chat-2"));
    }
}
