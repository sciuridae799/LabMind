package com.superagent.business.chat.chatagent.orchestration.model;

import com.superagent.business.chat.chatagent.execution.model.BusinessChatModelApiConfigSnapshot;
import java.time.Duration;

public record BusinessChatStartPlan(
        String question,
        String conversationId,
        String workspaceId,
        String authSessionToken,
        BusinessChatMode chatMode,
        BusinessChatModelApiConfigSnapshot modelConfig,
        Long selectedDocumentId,
        String selectedDocumentName,
        String traceId,
        String leaseKey,
        String leaseOwnerToken,
        Duration leaseTtl,
        long startAtEpochMillis) {
}
