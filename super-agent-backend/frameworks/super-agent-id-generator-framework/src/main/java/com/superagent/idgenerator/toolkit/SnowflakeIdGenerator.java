package com.superagent.idgenerator.toolkit;

public class SnowflakeIdGenerator {

    private static final long WORKER_ID_BITS = 5L;
    private static final long DATACENTER_ID_BITS = 5L;
    private static final long SEQUENCE_BITS = 12L;

    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);
    private static final long MAX_DATACENTER_ID = ~(-1L << DATACENTER_ID_BITS);
    private static final long SEQUENCE_MASK = ~(-1L << SEQUENCE_BITS);

    private static final long WORKER_ID_SHIFT = SEQUENCE_BITS;
    private static final long DATACENTER_ID_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS;
    private static final long TIMESTAMP_LEFT_SHIFT = SEQUENCE_BITS + WORKER_ID_BITS + DATACENTER_ID_BITS;

    private static final long DEFAULT_EPOCH = 1735689600000L;

    private final long workerId;
    private final long dataCenterId;
    private final long epoch;

    private long sequence;
    private long lastTimestamp = -1L;

    public SnowflakeIdGenerator(long workerId, long dataCenterId) {
        this(workerId, dataCenterId, DEFAULT_EPOCH);
    }

    SnowflakeIdGenerator(long workerId, long dataCenterId, long epoch) {
        if (workerId < 0 || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException("workerId must be between 0 and 31, but got: " + workerId);
        }
        if (dataCenterId < 0 || dataCenterId > MAX_DATACENTER_ID) {
            throw new IllegalArgumentException("dataCenterId must be between 0 and 31, but got: " + dataCenterId);
        }

        this.workerId = workerId;
        this.dataCenterId = dataCenterId;
        this.epoch = epoch;
    }

    public synchronized long nextId() {
        long timestamp = currentTimeMillis();
        if (timestamp < lastTimestamp) {
            throw new IllegalStateException("Clock moved backwards. Refusing to generate id.");
        }

        if (timestamp == lastTimestamp) {
            sequence = (sequence + 1) & SEQUENCE_MASK;
            if (sequence == 0) {
                timestamp = waitUntilNextMillis(lastTimestamp);
            }
        } else {
            sequence = 0L;
        }

        lastTimestamp = timestamp;
        return ((timestamp - epoch) << TIMESTAMP_LEFT_SHIFT)
                | (dataCenterId << DATACENTER_ID_SHIFT)
                | (workerId << WORKER_ID_SHIFT)
                | sequence;
    }

    public String getOrderNumber(long userId) {
        return Long.toString(userId) + nextId();
    }

    public long parseIdTimestamp(long id) {
        return (id >> TIMESTAMP_LEFT_SHIFT) + epoch;
    }

    protected long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    private long waitUntilNextMillis(long currentLastTimestamp) {
        long timestamp = currentTimeMillis();
        while (timestamp <= currentLastTimestamp) {
            timestamp = currentTimeMillis();
        }
        return timestamp;
    }
}
