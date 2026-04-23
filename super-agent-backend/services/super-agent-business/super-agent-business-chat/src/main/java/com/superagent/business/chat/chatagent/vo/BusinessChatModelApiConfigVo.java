package com.superagent.business.chat.chatagent.vo;

public record BusinessChatModelApiConfigVo(
        String id,
        String provider,
        String displayName,
        String baseUrl,
        String modelName,
        boolean apiKeyConfigured,
        boolean enabled) {
}
