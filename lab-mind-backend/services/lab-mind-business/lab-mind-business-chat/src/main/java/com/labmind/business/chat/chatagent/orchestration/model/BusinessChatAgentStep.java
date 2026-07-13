package com.labmind.business.chat.chatagent.orchestration.model;

import com.labmind.business.chat.chatagent.execution.agent.BusinessChatAgentType;

/**
 * 单轮回答中的 Agent 执行步骤。
 *
 * <p>它描述本轮真实执行链中的一个 Agent 角色。只有 answerProducer 为 true 的步骤会产生正文 token；
 * 其他步骤用于执行回答前后的确定性校验和观测归档。</p>
 */
public record BusinessChatAgentStep(
        BusinessChatAgentType agentType,
        String stageCode,
        String stageName,
        int stageOrder,
        boolean answerProducer) {

    public BusinessChatAgentStep {
        if (agentType == null) {
            throw new IllegalArgumentException("agentType is required");
        }
        if (stageCode == null || stageCode.isBlank()) {
            throw new IllegalArgumentException("stageCode is required");
        }
        if (stageName == null || stageName.isBlank()) {
            throw new IllegalArgumentException("stageName is required");
        }
    }
}
