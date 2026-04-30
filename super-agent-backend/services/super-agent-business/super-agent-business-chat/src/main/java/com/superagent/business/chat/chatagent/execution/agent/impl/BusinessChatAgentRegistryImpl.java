package com.superagent.business.chat.chatagent.execution.agent.impl;

import com.superagent.business.chat.chatagent.execution.agent.BusinessChatAgent;
import com.superagent.business.chat.chatagent.execution.agent.BusinessChatAgentRegistry;
import com.superagent.business.chat.chatagent.execution.agent.BusinessChatAgentType;
import com.superagent.business.chat.chatagent.service.BusinessChatErrorCode;
import com.superagent.common.frame.exception.BaseException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * Agent 注册表实现。
 *
 * <p>按 Agent 类型维护唯一实例，主服务根据编排计划选择 Agent，再由 Agent 继续委派到具体执行器。</p>
 *
 * <p>注册阶段必须完成全量一致性校验：</p>
 * <ul>
 *     <li>每个 Agent 必须声明非空 agentType。</li>
 *     <li>同一个 agentType 只能有一个实现。</li>
 *     <li>注册完成后用不可变 Map 承载，避免运行期映射被修改。</li>
 * </ul>
 */
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
