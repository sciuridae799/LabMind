package com.labmind.business.chat.auth;

import com.labmind.common.frame.exception.BaseException;

public class AuthException extends BaseException {

    public AuthException(AuthErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
