package com.labmind.business.chat.chatagent.execution.impl;

import com.labmind.business.chat.chatagent.orchestration.model.BusinessChatExecutionPlan;
import com.labmind.business.chat.chatagent.runtime.BusinessChatRuntimeContext;
import com.labmind.business.chat.chatagent.execution.BusinessChatExecutor;
import com.labmind.business.chat.chatagent.execution.BusinessChatDynamicModelClient;
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
