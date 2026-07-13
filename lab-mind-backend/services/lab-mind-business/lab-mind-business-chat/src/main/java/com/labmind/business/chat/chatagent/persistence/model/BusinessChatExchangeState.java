package com.labmind.business.chat.chatagent.persistence.model;

import com.labmind.business.chat.chatagent.service.BusinessChatErrorCode;
import com.labmind.common.frame.exception.BaseException;
import java.util.Arrays;
import lombok.Getter;
import org.springframework.util.StringUtils;

@Getter
public enum BusinessChatExchangeState {
    RUNNING(1, "RUNNING"),
    COMPLETED(2, "COMPLETED"),
    FAILED(3, "FAILED"),
    STOPPED(4, "STOPPED");

    private final int databaseCode;

    private final String value;

    BusinessChatExchangeState(int databaseCode, String value) {
        this.databaseCode = databaseCode;
        this.value = value;
    }

    public static BusinessChatExchangeState fromValue(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BaseException(BusinessChatErrorCode.CHAT_TURN_STATUS_INVALID, "turnStatus is required");
        }
        return Arrays.stream(values())
                .filter(state -> state.value.equalsIgnoreCase(value.trim()))
                .findFirst()
                .orElseThrow(() -> new BaseException(
                        BusinessChatErrorCode.CHAT_TURN_STATUS_INVALID,
                        "turnStatus is invalid: " + value));
    }

    public static BusinessChatExchangeState fromDatabaseCode(Integer databaseCode) {
        if (databaseCode == null) {
            throw new IllegalStateException("exchangeState databaseCode is null");
        }
        return Arrays.stream(values())
                .filter(state -> state.databaseCode == databaseCode)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "exchangeState databaseCode is invalid: " + databaseCode));
    }
}
