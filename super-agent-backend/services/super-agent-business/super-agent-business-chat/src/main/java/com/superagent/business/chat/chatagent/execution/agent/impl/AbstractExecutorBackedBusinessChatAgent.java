package com.superagent.business.chat.chatagent.execution.agent.impl;

import com.superagent.business.chat.chatagent.execution.agent.BusinessChatAgent;
import com.superagent.business.chat.chatagent.execution.BusinessChatExecutorRegistry;
import com.superagent.business.chat.chatagent.orchestration.model.BusinessChatExecutionPlan;
import com.superagent.business.chat.chatagent.runtime.BusinessChatRuntimeContext;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

/**
 * 基于执行器的 Agent 抽象类。
 *
 * <p>Agent 层只表达业务角色，正文生成能力按 executionMode 委派给对应 BusinessChatExecutor。
 * 这样编排层选择的是“业务角色”，执行器层负责“具体如何生成”。</p>
 *
 * <p>执行顺序：</p>
 * <ul>
 *     <li>如果编排计划明确短路，直接输出 shortCircuitReply。</li>
 *     <li>否则按 executionMode 从执行器注册表取执行器。</li>
 *     <li>执行器使用同一份 runtimeContext 和 executionPlan 继续完成模型输入组装与流式生成。</li>
 * </ul>
 */
abstract class AbstractExecutorBackedBusinessChatAgent implements BusinessChatAgent {

    private final BusinessChatExecutorRegistry executorRegistry;

    AbstractExecutorBackedBusinessChatAgent(BusinessChatExecutorRegistry executorRegistry) {
        this.executorRegistry = executorRegistry;
    }

    @Override
    public Flux<String> execute(BusinessChatRuntimeContext runtimeContext, BusinessChatExecutionPlan executionPlan) {
        /*
         * 短路是编排阶段给出的确定结果，Agent 层不再重新解释业务意图。
         * 如果 shortCircuit 为 true 但没有可返回内容，说明计划本身不完整，应立即失败。
         */
        if (executionPlan.shortCircuit()) {
            if (!StringUtils.hasText(executionPlan.shortCircuitReply())) {
                return Flux.error(new IllegalStateException("shortCircuitReply is required."));
            }
            return Flux.just(executionPlan.shortCircuitReply());
        }
        /*
         * 非短路请求进入执行器层。这里使用 getRequiredExecutor，保持执行模式缺失或未注册时直接暴露错误。
         */
        return executorRegistry.getRequiredExecutor(executionPlan.executionMode())
                .execute(runtimeContext, executionPlan);
    }
}
