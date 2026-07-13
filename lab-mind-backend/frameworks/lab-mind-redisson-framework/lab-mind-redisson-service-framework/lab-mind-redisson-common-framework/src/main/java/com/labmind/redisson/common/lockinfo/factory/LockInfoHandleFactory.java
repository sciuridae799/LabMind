package com.labmind.redisson.common.lockinfo.factory;

import java.util.Arrays;
import java.util.Collection;
import java.util.StringJoiner;
import org.springframework.util.StringUtils;

public class LockInfoHandleFactory {

    public String createLockName(String namespace, String... keys) {
        return createLockName(namespace, keys == null ? null : Arrays.asList(keys));
    }

    public String createLockName(String namespace, Collection<?> keys) {
        if (!StringUtils.hasText(namespace)) {
            throw new IllegalArgumentException("namespace must not be blank");
        }

        StringJoiner joiner = new StringJoiner(":");
        joiner.add(namespace.trim());

        if (keys == null) {
            return joiner.toString();
        }

        for (Object key : keys) {
            if (key == null) {
                throw new IllegalArgumentException("lock key must not be null");
            }
            String normalizedKey = key.toString().trim();
            if (!StringUtils.hasText(normalizedKey)) {
                throw new IllegalArgumentException("lock key must not be blank");
            }
            joiner.add(normalizedKey);
        }
        return joiner.toString();
    }
}
