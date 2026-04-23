package com.superagent.business.chat.chatagent.agent;

public interface BusinessChatAgentRegistry {

    BusinessChatAgent getRequiredAgent(BusinessChatAgentType agentType);
}
