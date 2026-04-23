package com.superagent.business.chat.chatagent.service;

import com.superagent.common.frame.enums.ErrorCode;
import lombok.Getter;

@Getter
public enum BusinessChatErrorCode implements ErrorCode {
    CHAT_MODE_INVALID("CHAT_400_001", "chat mode is invalid"),
    CHAT_TURN_STATUS_INVALID("CHAT_400_002", "turn status is invalid"),
    CHAT_MODEL_CONFIG_UNAVAILABLE("CHAT_400_003", "model config is unavailable"),
    CHAT_SESSION_NOT_FOUND("CHAT_404_001", "chat session was not found"),
    CHAT_MODEL_CONFIG_NOT_FOUND("CHAT_404_002", "model config was not found"),
    CHAT_RUNTIME_CONFLICT("CHAT_409_001", "conversation runtime already exists"),
    CHAT_SESSION_RUNNING("CHAT_409_002", "conversation is running and cannot be deleted"),
    CHAT_EXECUTOR_NOT_FOUND("CHAT_500_001", "executor for execution mode was not found"),
    CHAT_LEASE_RENEW_FAILED("CHAT_500_002", "conversation lease renew failed"),
    CHAT_EXECUTOR_REGISTRATION_INVALID("CHAT_500_003", "executor registration is invalid");

    private final String code;

    private final String message;

    BusinessChatErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

}
