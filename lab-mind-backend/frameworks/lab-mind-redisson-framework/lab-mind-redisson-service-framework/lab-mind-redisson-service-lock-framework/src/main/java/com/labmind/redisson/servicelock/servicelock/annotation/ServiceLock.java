package com.labmind.redisson.servicelock.servicelock.annotation;

import com.labmind.redisson.servicelock.core.LockType;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ServiceLock {

    String[] keys();

    LockType lockType() default LockType.Reentrant;

    long waitTime() default 0L;

    long leaseTime();

    TimeUnit timeUnit() default TimeUnit.MILLISECONDS;
}
