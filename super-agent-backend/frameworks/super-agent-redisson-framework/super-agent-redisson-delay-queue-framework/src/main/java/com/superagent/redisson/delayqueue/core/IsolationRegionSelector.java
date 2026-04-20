package com.superagent.redisson.delayqueue.core;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.util.StringUtils;

public class IsolationRegionSelector {

    private final int shardCount;

    private final ConcurrentHashMap<String, AtomicInteger> counters = new ConcurrentHashMap<>();

    public IsolationRegionSelector(int shardCount) {
        if (shardCount <= 0) {
            throw new IllegalArgumentException("shardCount must be greater than 0");
        }
        this.shardCount = shardCount;
    }

    public String selectTopic(String topic) {
        String normalizedTopic = normalizeTopic(topic);
        AtomicInteger counter = counters.computeIfAbsent(normalizedTopic, key -> new AtomicInteger(0));
        int shardIndex = Math.floorMod(counter.getAndIncrement(), shardCount);
        return shardTopic(normalizedTopic, shardIndex);
    }

    public List<String> listTopics(String topic) {
        String normalizedTopic = normalizeTopic(topic);
        List<String> shardTopics = new ArrayList<>(shardCount);
        for (int index = 0; index < shardCount; index++) {
            shardTopics.add(shardTopic(normalizedTopic, index));
        }
        return shardTopics;
    }

    public int shardCount() {
        return shardCount;
    }

    private String normalizeTopic(String topic) {
        if (!StringUtils.hasText(topic)) {
            throw new IllegalArgumentException("topic must not be blank");
        }
        return topic.trim();
    }

    private String shardTopic(String topic, int index) {
        return topic + "-" + index;
    }
}
