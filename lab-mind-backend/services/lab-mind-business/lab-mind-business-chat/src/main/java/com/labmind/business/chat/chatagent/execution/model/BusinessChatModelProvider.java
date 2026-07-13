package com.labmind.business.chat.chatagent.execution.model;

import com.labmind.business.chat.chatagent.service.BusinessChatErrorCode;
import com.labmind.common.frame.exception.BaseException;
import java.util.Arrays;

public enum BusinessChatModelProvider {

    DASHSCOPE("DASHSCOPE", "DASHSCOPE"),

    DEEPSEEK("DEEPSEEK", "DeepSeek"),

    ZHIPU("ZHIPU", "智谱AI");

    private final String value;

    private final String displayName;

    BusinessChatModelProvider(String value, String displayName) {
        this.value = value;
        this.displayName = displayName;
    }

    public String getValue() {
        return value;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static BusinessChatModelProvider fromValue(String value) {
        if (value == null || value.isBlank()) {
            throw new BaseException(BusinessChatErrorCode.CHAT_MODE_INVALID, "provider is required");
        }
        return Arrays.stream(values())
                .filter(provider -> provider.value.equalsIgnoreCase(value.strip()))
                .findFirst()
                .orElseThrow(() -> new BaseException(
                        BusinessChatErrorCode.CHAT_MODE_INVALID,
                        "provider is invalid: " + value));
    }
}
