package com.labmind.redisson.delayqueue.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spring.redis.redisson.delay-queue")
public class DelayQueueProperties {

    private Integer shardCount;

    private Duration consumerPollTimeout;

    public Integer getShardCount() {
        return shardCount;
    }

    public void setShardCount(Integer shardCount) {
        this.shardCount = shardCount;
    }

    public Duration getConsumerPollTimeout() {
        return consumerPollTimeout;
    }

    public void setConsumerPollTimeout(Duration consumerPollTimeout) {
        this.consumerPollTimeout = consumerPollTimeout;
    }
}
