package com.superagent.business.chat.chatagent.execution.agent;

import com.superagent.business.chat.chatagent.orchestration.model.BusinessChatExecutionPlan;
import com.superagent.business.chat.chatagent.runtime.BusinessChatRuntimeContext;
import reactor.core.publisher.Flux;

/**
 * 业务聊天 Agent 的最小执行契约。
 *
 * <p>Agent 位于编排计划和底层执行器之间：编排器先确定本轮对话需要的业务角色，
 * 服务层再按 {@link BusinessChatAgentType} 取到唯一 Agent 实例并调用执行。</p>
 *
 * <p>这个接口只约束两件事：</p>
 * <ul>
 *     <li>{@link #agentType()} 声明当前 Agent 承接的业务角色。</li>
 *     <li>{@link #execute(BusinessChatRuntimeContext, BusinessChatExecutionPlan)} 基于运行态上下文和执行计划输出增量文本流。</li>
 * </ul>
 */
public interface BusinessChatAgent {

    /**
     * 当前 Agent 支持的业务角色类型。
     */
    BusinessChatAgentType agentType();

    /**
     * 执行本轮 Agent 任务。
     *
     * <p>输入由两部分组成：runtimeContext 承载会话、用户、租户等运行态信息；
     * executionPlan 承载编排阶段已经确定的意图、模式、证据、澄清和短路信息。
     * 输出保持为 {@link Flux}，用于直接对接聊天流式响应链路。</p>
     */
    Flux<String> execute(BusinessChatRuntimeContext runtimeContext, BusinessChatExecutionPlan executionPlan);
}
