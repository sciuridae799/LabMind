package com.superagent.business.chat.chatagent.agent.impl;

import com.superagent.business.chat.chatagent.agent.BusinessChatAgent;
import com.superagent.business.chat.chatagent.agent.BusinessChatAgentRegistry;
import com.superagent.business.chat.chatagent.agent.BusinessChatAgentType;
import com.superagent.business.chat.chatagent.service.BusinessChatErrorCode;
import com.superagent.common.frame.exception.BaseException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class BusinessChatAgentRegistryImpl implements BusinessChatAgentRegistry {

    private final Map<BusinessChatAgentType, BusinessChatAgent> agentMap;

    public BusinessChatAgentRegistryImpl(List<BusinessChatAgent> agentList) {
        EnumMap<BusinessChatAgentType, BusinessChatAgent> registeredAgentMap = new EnumMap<>(BusinessChatAgentType.class);
        for (BusinessChatAgent agent : agentList) {
            BusinessChatAgentType agentType = requireAgentType(agent);
            BusinessChatAgent existingAgent = registeredAgentMap.putIfAbsent(agentType, agent);
            if (existingAgent != null) {
                throw new BaseException(
                        BusinessChatErrorCode.CHAT_EXECUTOR_REGISTRATION_INVALID,
                        "Duplicate agent for agent type: " + agentType.getValue());
            }
        }
        this.agentMap = Map.copyOf(registeredAgentMap);
    }

    @Override
    public BusinessChatAgent getRequiredAgent(BusinessChatAgentType agentType) {
        if (agentType == null) {
            throw new BaseException(
                    BusinessChatErrorCode.CHAT_EXECUTOR_NOT_FOUND,
                    "agent type is required");
        }
        BusinessChatAgent agent = agentMap.get(agentType);
        if (agent == null) {
            throw new BaseException(
                    BusinessChatErrorCode.CHAT_EXECUTOR_NOT_FOUND,
                    "No agent supports agent type: " + agentType.getValue());
        }
        return agent;
    }

    private BusinessChatAgentType requireAgentType(BusinessChatAgent agent) {
        BusinessChatAgentType agentType = agent.agentType();
        if (agentType == null) {
            throw new BaseException(
                    BusinessChatErrorCode.CHAT_EXECUTOR_REGISTRATION_INVALID,
                    "agent type is required: " + agent.getClass().getName());
        }
        return agentType;
    }
}
