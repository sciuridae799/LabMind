package com.superagent.business.chat.chatagent.agent;

import com.superagent.business.chat.chatagent.model.BusinessChatExecutionPlan;
import com.superagent.business.chat.chatagent.runtime.BusinessChatRuntimeContext;
import reactor.core.publisher.Flux;

public interface BusinessChatAgent {

    BusinessChatAgentType agentType();

    Flux<String> execute(BusinessChatRuntimeContext runtimeContext, BusinessChatExecutionPlan executionPlan);
}
