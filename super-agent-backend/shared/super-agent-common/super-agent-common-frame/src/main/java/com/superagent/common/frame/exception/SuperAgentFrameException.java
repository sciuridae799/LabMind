package com.superagent.common.frame.exception;

import com.superagent.common.frame.enums.ErrorCode;

public class SuperAgentFrameException extends BaseException {

    public SuperAgentFrameException(ErrorCode errorCode) {
        super(errorCode);
    }

    public SuperAgentFrameException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public SuperAgentFrameException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public SuperAgentFrameException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
