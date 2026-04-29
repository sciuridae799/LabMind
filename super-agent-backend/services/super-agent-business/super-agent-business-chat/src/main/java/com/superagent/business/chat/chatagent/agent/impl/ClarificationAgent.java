package com.superagent.business.chat.chatagent.agent.impl;

import com.superagent.business.chat.chatagent.agent.BusinessChatAgent;
import com.superagent.business.chat.chatagent.agent.BusinessChatAgentType;
import com.superagent.business.chat.chatagent.model.BusinessChatExecutionPlan;
import com.superagent.business.chat.chatagent.runtime.BusinessChatRuntimeContext;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

@Service
public class ClarificationAgent implements BusinessChatAgent {

    @Override
    public BusinessChatAgentType agentType() {
        return BusinessChatAgentType.CLARIFICATION;
    }

    @Override
    public Flux<String> execute(BusinessChatRuntimeContext runtimeContext, BusinessChatExecutionPlan executionPlan) {
        if (executionPlan.clarificationPlan() == null || !executionPlan.clarificationPlan().required()) {
            throw new IllegalStateException("clarification plan is required for CLARIFICATION agent.");
        }
        String reply = executionPlan.clarificationPlan().reply();
        if (!StringUtils.hasText(reply)) {
            throw new IllegalStateException("clarification reply must not be blank.");
        }
        return Flux.just(reply);
    }
}
