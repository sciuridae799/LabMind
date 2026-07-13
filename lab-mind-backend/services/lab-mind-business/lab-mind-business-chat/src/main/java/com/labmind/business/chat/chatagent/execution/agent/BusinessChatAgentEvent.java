package com.labmind.business.chat.chatagent.execution.agent;

/**
 * Agent 执行过程事件。
 *
 * <p>该事件用于把 Agent 层的执行状态转成前端可消费的流式事件：
 * agentType 标识业务角色，agentName 提供展示名称，message 承载本次事件文本。</p>
 */
public record BusinessChatAgentEvent(
        BusinessChatAgentType agentType,
        String agentName,
        String message) {
}
