package com.superagent.business.chat.chatagent.model;

public record BusinessChatModelApiConfigSnapshot(
        Long id,
        BusinessChatModelProvider provider,
        String displayName,
        String baseUrl,
        String modelName,
        String apiKey) {
}
