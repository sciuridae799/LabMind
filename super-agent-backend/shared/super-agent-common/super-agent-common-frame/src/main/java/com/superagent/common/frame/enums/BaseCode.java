package com.superagent.common.frame.enums;

public enum BaseCode implements ErrorCode {

    SUCCESS("0", "success"),
    INVALID_PARAMETER("400", "invalid parameter"),
    NOT_FOUND("404", "resource not found"),
    METHOD_NOT_ALLOWED("405", "method not allowed"),
    SYSTEM_ERROR("500", "system error");

    private final String code;

    private final String message;

    BaseCode(String code, String message) {
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
