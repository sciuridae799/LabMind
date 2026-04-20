package com.superagent.redisson.repeatexecutelimit.constant;

public final class RepeatExecuteLimitConstant {

    public static final String SUCCESS_FLAG = "SUCCESS";

    public static final String SUCCESS_PREFIX = "repeat-execute-limit:success";

    public static final String LOCAL_LOCK_PREFIX = "repeat-execute-limit:local-lock";

    public static final String DISTRIBUTED_LOCK_PREFIX = "repeat-execute-limit:distributed-lock";

    private RepeatExecuteLimitConstant() {
    }
}
