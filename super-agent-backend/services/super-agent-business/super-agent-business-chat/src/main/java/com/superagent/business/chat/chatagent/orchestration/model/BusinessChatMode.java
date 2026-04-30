package com.superagent.business.chat.chatagent.orchestration.model;

import com.superagent.business.chat.chatagent.service.BusinessChatErrorCode;
import com.superagent.common.frame.exception.BaseException;
import java.util.Arrays;
import lombok.Getter;
import org.springframework.util.StringUtils;

@Getter
public enum BusinessChatMode {

    CURRENT_DOCUMENT(1, "CURRENT_DOCUMENT", "当前文档问答"),
    KNOWLEDGE_BASE(2, "KNOWLEDGE_BASE", "自动知识问答"),
    OPEN_ENDED(3, "OPEN_ENDED", "开放式提问");

    private final int databaseCode;

    private final String value;

    private final String displayName;

    BusinessChatMode(int databaseCode, String value, String displayName) {
        this.databaseCode = databaseCode;
        this.value = value;
        this.displayName = displayName;
    }

    public static BusinessChatMode fromValue(String value) {
        if (!StringUtils.hasText(value)) {
            throw new BaseException(BusinessChatErrorCode.CHAT_MODE_INVALID, "chatMode is required");
        }
        return Arrays.stream(values())
                .filter(mode -> mode.value.equalsIgnoreCase(value.trim()))
                .findFirst()
                .orElseThrow(() -> new BaseException(
                        BusinessChatErrorCode.CHAT_MODE_INVALID,
                        "chatMode is invalid: " + value));
    }

    public static BusinessChatMode fromDatabaseCode(Integer databaseCode) {
        if (databaseCode == null) {
            throw new IllegalStateException("chatMode databaseCode is null");
        }
        return Arrays.stream(values())
                .filter(mode -> mode.databaseCode == databaseCode)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("chatMode databaseCode is invalid: " + databaseCode));
    }
}
