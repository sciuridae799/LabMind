package com.labmind.business.chat.chatagent.api.vo;

import java.util.List;

public record BusinessChatStreamEvent(
        String eventType,
        String conversationId,
        String exchangeId,
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

    public static BusinessChatStreamEvent message(
            String eventType,
            String conversationId,
            Long exchangeId,
            String chatMode,
            String message,
            Long firstTokenLatencyMs,
            Long totalLatencyMs) {
        return new BusinessChatStreamEvent(
                eventType,
                conversationId,
                stringifyExchangeId(exchangeId),
                chatMode,
                null,
                null,
                null,
                null,
                message,
                firstTokenLatencyMs,
                totalLatencyMs);
    }

    public static BusinessChatStreamEvent textDelta(
            String conversationId,
            Long exchangeId,
            String chatMode,
            String textDelta,
            Long firstTokenLatencyMs) {
        return new BusinessChatStreamEvent(
                "TEXT_DELTA",
                conversationId,
                stringifyExchangeId(exchangeId),
                chatMode,
                textDelta,
                null,
                null,
                null,
                null,
                firstTokenLatencyMs,
                null);
    }

    public static BusinessChatStreamEvent functionSupplement(
            String conversationId,
            Long exchangeId,
            String chatMode,
            String functionSupplement,
            Long firstTokenLatencyMs) {
        return new BusinessChatStreamEvent(
                "FUNCTION_SUPPLEMENT",
                conversationId,
                stringifyExchangeId(exchangeId),
                chatMode,
                null,
                functionSupplement,
                null,
                null,
                null,
                firstTokenLatencyMs,
                null);
    }

    public static BusinessChatStreamEvent sourceSnapshotList(
            String conversationId,
            Long exchangeId,
            String chatMode,
            List<String> sourceSnapshotList,
            Long firstTokenLatencyMs) {
        return new BusinessChatStreamEvent(
                "REFERENCE_SUPPLEMENT",
                conversationId,
                stringifyExchangeId(exchangeId),
                chatMode,
                null,
                null,
                sourceSnapshotList,
                null,
                null,
                firstTokenLatencyMs,
                null);
    }

    public static BusinessChatStreamEvent followUpSuggestionList(
            String conversationId,
            Long exchangeId,
            String chatMode,
            List<String> followUpSuggestionList,
            Long firstTokenLatencyMs) {
        return new BusinessChatStreamEvent(
                "FOLLOW_UP_RECOMMENDATION",
                conversationId,
                stringifyExchangeId(exchangeId),
                chatMode,
                null,
                null,
                null,
                followUpSuggestionList,
                null,
                firstTokenLatencyMs,
                null);
    }

    public static BusinessChatStreamEvent agent(
            String eventType,
            String conversationId,
            Long exchangeId,
            String chatMode,
            String message,
            String agentType,
            String agentName,
            Long firstTokenLatencyMs) {
        return new BusinessChatStreamEvent(
                eventType,
                conversationId,
                stringifyExchangeId(exchangeId),
                chatMode,
                null,
                null,
                null,
                null,
                message,
                agentType,
                agentName,
                firstTokenLatencyMs,
                null);
    }

    public BusinessChatStreamEvent(
            String eventType,
            String conversationId,
            String exchangeId,
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

    private static String stringifyExchangeId(Long exchangeId) {
        return exchangeId == null ? null : String.valueOf(exchangeId);
    }
}
