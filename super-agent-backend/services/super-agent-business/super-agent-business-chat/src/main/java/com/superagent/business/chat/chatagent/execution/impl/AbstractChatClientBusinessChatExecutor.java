package com.superagent.business.chat.chatagent.execution.impl;

import com.superagent.business.chat.chatagent.orchestration.model.BusinessChatExecutionPlan;
import com.superagent.business.chat.chatagent.runtime.BusinessChatRuntimeContext;
import com.superagent.business.chat.chatagent.execution.BusinessChatExecutor;
import com.superagent.business.chat.chatagent.execution.BusinessChatDynamicModelClient;
import reactor.core.publisher.Flux;

abstract class AbstractChatClientBusinessChatExecutor implements BusinessChatExecutor {

    private final BusinessChatDynamicModelClient modelClient;

    AbstractChatClientBusinessChatExecutor(BusinessChatDynamicModelClient modelClient) {
        this.modelClient = modelClient;
    }

    @Override
    public Flux<String> execute(BusinessChatRuntimeContext runtimeContext, BusinessChatExecutionPlan executionPlan) {
        return modelClient.stream(runtimeContext, executionPlan);
    }
}
