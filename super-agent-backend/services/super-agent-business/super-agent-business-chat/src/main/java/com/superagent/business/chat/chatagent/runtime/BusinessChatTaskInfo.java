package com.superagent.business.chat.chatagent.runtime;

import com.superagent.business.chat.chatagent.execution.model.BusinessChatModelApiConfigSnapshot;
import com.superagent.business.chat.chatagent.orchestration.model.BusinessChatMode;
import java.time.Duration;

/**
 * 单轮问答任务快照。
 *
 * <p>记录创建 exchange 后不可变的执行输入，包括会话、模式、模型配置、选中文档和租约信息。</p>
 */
public record BusinessChatTaskInfo(
        Long dialogueId,
        Long exchangeId,
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
