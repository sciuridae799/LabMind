package com.superagent.common.frame.exception;

import com.superagent.common.frame.enums.BaseCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BaseExceptionTest {

    @Test
    void shouldExposeErrorCodeMetadata() {
        BaseException exception = new BaseException(BaseCode.INVALID_PARAMETER, "page size must be greater than 0");

        assertThat(exception.getErrorCode()).isEqualTo(BaseCode.INVALID_PARAMETER);
        assertThat(exception.getCode()).isEqualTo("400");
        assertThat(exception.getMessage()).isEqualTo("page size must be greater than 0");
    }

    @Test
    void shouldRejectBlankExceptionMessage() {
        assertThatThrownBy(() -> new BaseException(BaseCode.SYSTEM_ERROR, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank");
    }
}
