package com.labmind.business.chat.chatagent.orchestration.model;

public record BusinessChatIntentAnalysis(
        String intentLabel,
        String intentReason,
        BusinessChatMode executionMode) {
}
