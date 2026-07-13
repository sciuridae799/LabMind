package com.labmind.redisson.delayqueue.event;

public interface ConsumerTask {

    String topic();

    void consume(Object content) throws Exception;
}
