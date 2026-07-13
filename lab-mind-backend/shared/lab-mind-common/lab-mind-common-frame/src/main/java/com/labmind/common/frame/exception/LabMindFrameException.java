package com.labmind.common.frame.exception;

import com.labmind.common.frame.enums.ErrorCode;

public class LabMindFrameException extends BaseException {

    public LabMindFrameException(ErrorCode errorCode) {
        super(errorCode);
    }

    public LabMindFrameException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }

    public LabMindFrameException(ErrorCode errorCode, Throwable cause) {
        super(errorCode, cause);
    }

    public LabMindFrameException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }
}
