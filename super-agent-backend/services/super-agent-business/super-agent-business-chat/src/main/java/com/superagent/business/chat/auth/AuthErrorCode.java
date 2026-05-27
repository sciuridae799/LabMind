package com.superagent.business.chat.auth;

import com.superagent.common.frame.enums.ErrorCode;

public enum AuthErrorCode implements ErrorCode {

    AUTH_REQUIRED("401", "auth required"),
    AUTH_FORBIDDEN("403", "auth forbidden");

    private final String code;

    private final String message;

    AuthErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }
}
