package com.labmind.common.frame.exception;

import com.labmind.common.frame.enums.ErrorCode;
import java.io.Serial;
import java.util.Objects;
import org.springframework.util.Assert;

public class BaseException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final ErrorCode errorCode;

    public BaseException(ErrorCode errorCode) {
        super(requireErrorCode(errorCode).getMessage());
        this.errorCode = errorCode;
    }

    public BaseException(ErrorCode errorCode, String message) {
        super(requireText(message));
        this.errorCode = requireErrorCode(errorCode);
    }

    public BaseException(ErrorCode errorCode, Throwable cause) {
        super(requireErrorCode(errorCode).getMessage(), cause);
        this.errorCode = errorCode;
    }

    public BaseException(ErrorCode errorCode, String message, Throwable cause) {
        super(requireText(message), cause);
        this.errorCode = requireErrorCode(errorCode);
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public String getCode() {
        return errorCode.getCode();
    }

    private static ErrorCode requireErrorCode(ErrorCode errorCode) {
        return Objects.requireNonNull(errorCode, "errorCode must not be null");
    }

    private static String requireText(String message) {
        Assert.hasText(message, "exception message must not be blank");
        return message;
    }
}
