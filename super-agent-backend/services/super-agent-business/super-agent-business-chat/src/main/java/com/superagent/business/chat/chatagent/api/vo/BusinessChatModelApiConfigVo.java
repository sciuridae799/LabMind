package com.superagent.business.chat.chatagent.api.vo;

import java.math.BigDecimal;

public record BusinessChatModelApiConfigVo(
        String id,
        String provider,
        String displayName,
        String baseUrl,
        String modelName,
        BigDecimal inputTokenUnitPrice,
        BigDecimal outputTokenUnitPrice,
        int priceUnitTokens,
        String currency,
        boolean apiKeyConfigured,
        boolean enabled) {
}
