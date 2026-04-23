package com.superagent.business.chat.chatagent.agent;

public record BusinessChatAgentEvent(
        BusinessChatAgentType agentType,
        String agentName,
        String message) {
}
