package com.superagent.business.chat.chatagent.model;

import java.time.Duration;

public record BusinessChatStartPlan(
        String question,
        String conversationId,
        BusinessChatMode chatMode,
        BusinessChatModelApiConfigSnapshot modelConfig,
        String traceId,
        String leaseKey,
        String leaseOwnerToken,
        Duration leaseTtl,
        long startAtEpochMillis) {
}
