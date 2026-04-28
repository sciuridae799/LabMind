package com.superagent.business.chat.chatagent.agent.impl;

import com.superagent.business.chat.chatagent.agent.BusinessChatAgent;
import com.superagent.business.chat.chatagent.execution.BusinessChatExecutorRegistry;
import com.superagent.business.chat.chatagent.model.BusinessChatExecutionPlan;
import com.superagent.business.chat.chatagent.runtime.BusinessChatRuntimeContext;
import reactor.core.publisher.Flux;

/**
 * 基于执行器的 Agent 抽象类。
 *
 * <p>Agent 层只表达业务角色，正文生成能力按 executionMode 委派给对应 BusinessChatExecutor。</p>
 */
abstract class AbstractExecutorBackedBusinessChatAgent implements BusinessChatAgent {

    private final BusinessChatExecutorRegistry executorRegistry;

    AbstractExecutorBackedBusinessChatAgent(BusinessChatExecutorRegistry executorRegistry) {
        this.executorRegistry = executorRegistry;
    }

    @Override
    public Flux<String> execute(BusinessChatRuntimeContext runtimeContext, BusinessChatExecutionPlan executionPlan) {
        return executorRegistry.getRequiredExecutor(executionPlan.executionMode())
                .execute(runtimeContext, executionPlan);
    }
}
