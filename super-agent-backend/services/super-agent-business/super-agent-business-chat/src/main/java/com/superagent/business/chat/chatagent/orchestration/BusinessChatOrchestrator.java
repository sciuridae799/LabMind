package com.superagent.business.chat.chatagent.orchestration;

import com.superagent.business.chat.chatagent.orchestration.model.BusinessChatExecutionPlan;
import com.superagent.business.chat.chatagent.runtime.BusinessChatRuntimeContext;

/**
 * 对话编排器。
 *
 * <p>运行态 -> 执行计划。</p>
 */
public interface BusinessChatOrchestrator {

    /**
     * 运行态输入 -> 本轮执行计划。
     *
     * @param runtimeContext 本轮运行上下文
     * @return 本轮执行计划
     */
    BusinessChatExecutionPlan orchestrate(BusinessChatRuntimeContext runtimeContext);
}
