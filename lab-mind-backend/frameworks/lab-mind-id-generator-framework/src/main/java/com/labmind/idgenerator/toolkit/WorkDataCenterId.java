package com.labmind.idgenerator.toolkit;

public record WorkDataCenterId(long workId, long dataCenterId) {

    public static final long MIN_VALUE = 0L;
    public static final long MAX_VALUE = 31L;

    public WorkDataCenterId {
        validateRange(workId, "workId");
        validateRange(dataCenterId, "dataCenterId");
    }

    private static void validateRange(long value, String fieldName) {
        if (value < MIN_VALUE || value > MAX_VALUE) {
            throw new IllegalArgumentException(fieldName + " must be between 0 and 31, but got: " + value);
        }
    }
}
