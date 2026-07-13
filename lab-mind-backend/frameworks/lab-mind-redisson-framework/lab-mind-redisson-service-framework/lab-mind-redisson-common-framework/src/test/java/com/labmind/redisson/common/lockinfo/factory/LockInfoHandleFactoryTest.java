package com.labmind.redisson.common.lockinfo.factory;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LockInfoHandleFactoryTest {

    private final LockInfoHandleFactory lockInfoHandleFactory = new LockInfoHandleFactory();

    @Test
    void shouldJoinNamespaceAndKeysWithColon() {
        assertThat(lockInfoHandleFactory.createLockName("chat", "session", "42"))
                .isEqualTo("chat:session:42");
    }

    @Test
    void shouldRejectBlankKey() {
        assertThatThrownBy(() -> lockInfoHandleFactory.createLockName("chat", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("lock key");
    }
}
