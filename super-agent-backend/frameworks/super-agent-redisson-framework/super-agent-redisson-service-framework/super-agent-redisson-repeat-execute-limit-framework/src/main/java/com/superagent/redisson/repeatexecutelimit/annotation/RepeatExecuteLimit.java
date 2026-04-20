package com.superagent.redisson.repeatexecutelimit.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface RepeatExecuteLimit {

    String[] keys();

    long waitTime() default 0L;

    long lockLeaseTime();

    long successLeaseTime();

    TimeUnit timeUnit() default TimeUnit.MILLISECONDS;
}
