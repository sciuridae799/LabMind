package com.superagent.idgenerator.toolkit;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SnowflakeIdGeneratorTest {

    @Test
    void shouldGenerateIncreasingUniqueIds() {
        SnowflakeIdGenerator generator = new SnowflakeIdGenerator(3, 7);

        long firstId = generator.nextId();
        long secondId = generator.nextId();

        assertThat(secondId).isGreaterThan(firstId);
        assertThat(extractWorkerId(firstId)).isEqualTo(3);
        assertThat(extractDataCenterId(firstId)).isEqualTo(7);
    }

    @Test
    void shouldGenerateOrderNumberFromUserIdAndSnowflakeId() {
        SnowflakeIdGenerator generator = new FixedTimeSnowflakeIdGenerator(1, 2, 1735776000000L);

        String orderNumber = generator.getOrderNumber(9527L);

        assertThat(orderNumber).startsWith("9527");
        assertThat(orderNumber.length()).isGreaterThan("9527".length());
    }

    @Test
    void shouldParseTimestampFromGeneratedId() {
        long timestamp = 1735776000000L;
        SnowflakeIdGenerator generator = new FixedTimeSnowflakeIdGenerator(4, 5, timestamp);

        long id = generator.nextId();

        assertThat(generator.parseIdTimestamp(id)).isEqualTo(timestamp);
    }

    @Test
    void shouldRejectClockRollback() throws Exception {
        long timestamp = 1735776000001L;
        SnowflakeIdGenerator generator = new FixedTimeSnowflakeIdGenerator(1, 1, timestamp - 1);
        setField(generator, "lastTimestamp", timestamp);

        assertThatThrownBy(generator::nextId)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Clock moved backwards");
    }

    @Test
    void shouldWaitForNextMillisecondWhenSequenceIsExhausted() throws Exception {
        long timestamp = 1735776000000L;
        SnowflakeIdGenerator generator = new FixedTimeSnowflakeIdGenerator(2, 6, timestamp, timestamp, timestamp + 1);
        setField(generator, "lastTimestamp", timestamp);
        setField(generator, "sequence", 4095L);

        long id = generator.nextId();

        assertThat(generator.parseIdTimestamp(id)).isEqualTo(timestamp + 1);
        assertThat(extractSequence(id)).isZero();
    }

    @Test
    void shouldRejectOutOfRangeMachineIds() {
        assertThatThrownBy(() -> new SnowflakeIdGenerator(32, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("workerId");
        assertThatThrownBy(() -> new SnowflakeIdGenerator(0, 32))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("dataCenterId");
    }

    private static long extractWorkerId(long id) {
        return (id >> 12) & 31L;
    }

    private static long extractDataCenterId(long id) {
        return (id >> 17) & 31L;
    }

    private static long extractSequence(long id) {
        return id & 4095L;
    }

    private static void setField(Object target, String fieldName, long value) throws Exception {
        Field field = SnowflakeIdGenerator.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.setLong(target, value);
    }

    private static final class FixedTimeSnowflakeIdGenerator extends SnowflakeIdGenerator {

        private final long[] timestamps;
        private int index;

        private FixedTimeSnowflakeIdGenerator(long workerId, long dataCenterId, long... timestamps) {
            super(workerId, dataCenterId);
            this.timestamps = timestamps;
        }

        @Override
        protected long currentTimeMillis() {
            int currentIndex = Math.min(index, timestamps.length - 1);
            long timestamp = timestamps[currentIndex];
            index++;
            return timestamp;
        }
    }
}
