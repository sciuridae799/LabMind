package com.labmind.business.chat.chatagent.execution.model;

import java.math.BigDecimal;

public record BusinessChatModelApiConfigSnapshot(
        Long id,
        BusinessChatModelProvider provider,
        String displayName,
        String baseUrl,
        String modelName,
        String apiKey,
        BigDecimal inputTokenUnitPrice,
        BigDecimal outputTokenUnitPrice,
        int priceUnitTokens,
        String currency) {
}
