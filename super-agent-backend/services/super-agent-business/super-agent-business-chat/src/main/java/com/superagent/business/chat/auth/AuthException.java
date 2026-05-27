package com.superagent.business.chat.auth;

import com.superagent.common.frame.exception.BaseException;

public class AuthException extends BaseException {

    public AuthException(AuthErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}
