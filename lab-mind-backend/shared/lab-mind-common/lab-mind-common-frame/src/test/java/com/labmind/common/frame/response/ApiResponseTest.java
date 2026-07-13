package com.labmind.common.frame.response;

import com.labmind.common.frame.enums.BaseCode;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApiResponseTest {

    @Test
    void shouldCreateSuccessResponse() {
        ApiResponse<String> response = ApiResponse.ok("done");

        assertThat(response.getCode()).isEqualTo("0");
        assertThat(response.getMessage()).isEqualTo("success");
        assertThat(response.getData()).isEqualTo("done");
    }

    @Test
    void shouldCreateErrorResponse() {
        ApiResponse<Void> response = ApiResponse.error(BaseCode.INVALID_PARAMETER, "current must be greater than 0");

        assertThat(response.getCode()).isEqualTo("400");
        assertThat(response.getMessage()).isEqualTo("current must be greater than 0");
        assertThat(response.getData()).isNull();
    }

    @Test
    void shouldRejectBlankErrorMessage() {
        assertThatThrownBy(() -> ApiResponse.error(BaseCode.SYSTEM_ERROR, " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must not be blank");
    }
}
