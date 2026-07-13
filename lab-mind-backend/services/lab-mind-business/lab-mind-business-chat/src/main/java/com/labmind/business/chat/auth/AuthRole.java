package com.labmind.business.chat.auth;

import com.labmind.common.frame.enums.BaseCode;
import com.labmind.common.frame.exception.BaseException;
import java.util.Arrays;

public enum AuthRole {

    GUEST("guest"),
    USER("user"),
    SUPER_ADMIN("super_admin");

    private final String value;

    AuthRole(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }

    public static AuthRole fromValue(String value) {
        String normalized = value == null ? null : value.strip();
        return Arrays.stream(values())
                .filter(role -> role.value.equals(normalized))
                .findFirst()
                .orElseThrow(() -> new BaseException(BaseCode.INVALID_PARAMETER, "role is invalid"));
    }
}
