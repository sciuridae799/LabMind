package com.superagent.common.frame.response;

import com.superagent.common.frame.enums.BaseCode;
import com.superagent.common.frame.enums.ErrorCode;
import java.io.Serial;
import java.io.Serializable;
import java.util.Objects;
import org.springframework.util.Assert;

public final class ApiResponse<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String code;

    private final String message;

    private final T data;

    private ApiResponse(String code, String message, T data) {
        Assert.hasText(code, "response code must not be blank");
        Assert.hasText(message, "response message must not be blank");
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static ApiResponse<Void> ok() {
        return ok(null);
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(BaseCode.SUCCESS.getCode(), BaseCode.SUCCESS.getMessage(), data);
    }

    public static ApiResponse<Void> error(ErrorCode errorCode) {
        return error(errorCode, errorCode.getMessage(), null);
    }

    public static ApiResponse<Void> error(ErrorCode errorCode, String message) {
        return error(errorCode, message, null);
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, T data) {
        return error(errorCode, errorCode.getMessage(), data);
    }

    public static <T> ApiResponse<T> error(ErrorCode errorCode, String message, T data) {
        Objects.requireNonNull(errorCode, "errorCode must not be null");
        Assert.hasText(message, "response message must not be blank");
        return new ApiResponse<>(errorCode.getCode(), message, data);
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public T getData() {
        return data;
    }
}
