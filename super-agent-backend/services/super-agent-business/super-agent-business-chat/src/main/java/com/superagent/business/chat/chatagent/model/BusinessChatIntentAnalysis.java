package com.superagent.business.chat.chatagent.model;

public record BusinessChatIntentAnalysis(
        String intentLabel,
        String intentReason,
        BusinessChatMode executionMode) {
}
