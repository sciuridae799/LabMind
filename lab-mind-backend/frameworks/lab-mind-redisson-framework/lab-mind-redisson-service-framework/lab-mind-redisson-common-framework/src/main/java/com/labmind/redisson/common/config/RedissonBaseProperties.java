package com.labmind.redisson.common.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spring.redis.redisson")
public class RedissonBaseProperties {

    private String protocol;

    private Integer threads;

    private Integer nettyThreads;

    private Duration localLockCacheTtl;

    public String getProtocol() {
        return protocol;
    }

    public void setProtocol(String protocol) {
        this.protocol = protocol;
    }

    public Integer getThreads() {
        return threads;
    }

    public void setThreads(Integer threads) {
        this.threads = threads;
    }

    public Integer getNettyThreads() {
        return nettyThreads;
    }

    public void setNettyThreads(Integer nettyThreads) {
        this.nettyThreads = nettyThreads;
    }

    public Duration getLocalLockCacheTtl() {
        return localLockCacheTtl;
    }

    public void setLocalLockCacheTtl(Duration localLockCacheTtl) {
        this.localLockCacheTtl = localLockCacheTtl;
    }
}
