package com.superagent.business.chat.chatagent.orchestration.model;

public record BusinessChatIntentAnalysis(
        String intentLabel,
        String intentReason,
        BusinessChatMode executionMode) {
}
