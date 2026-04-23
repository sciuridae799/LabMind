package com.superagent.business.chat.chatagent.vo;

import java.util.List;

public record BusinessChatStreamEvent(
        String eventType,
        String conversationId,
        Long exchangeId,
        String chatMode,
        String textDelta,
        String functionSupplement,
        List<String> sourceSnapshotList,
        List<String> followUpSuggestionList,
        String message,
        String agentType,
        String agentName,
        Long firstTokenLatencyMs,
        Long totalLatencyMs) {

    public BusinessChatStreamEvent(
            String eventType,
            String conversationId,
            Long exchangeId,
            String chatMode,
            String textDelta,
            String functionSupplement,
            List<String> sourceSnapshotList,
            List<String> followUpSuggestionList,
            String message,
            Long firstTokenLatencyMs,
            Long totalLatencyMs) {
        this(
                eventType,
                conversationId,
                exchangeId,
                chatMode,
                textDelta,
                functionSupplement,
                sourceSnapshotList,
                followUpSuggestionList,
                message,
                null,
                null,
                firstTokenLatencyMs,
                totalLatencyMs);
    }
}
