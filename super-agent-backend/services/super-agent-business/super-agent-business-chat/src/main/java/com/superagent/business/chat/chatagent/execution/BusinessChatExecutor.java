package com.superagent.business.chat.chatagent.execution;

import com.superagent.business.chat.chatagent.model.BusinessChatExecutionPlan;
import com.superagent.business.chat.chatagent.model.BusinessChatMode;
import com.superagent.business.chat.chatagent.runtime.BusinessChatRuntimeContext;
import reactor.core.publisher.Flux;

/**
 * 对话执行器。
 *
 * <p>执行计划 -> 正文增量。</p>
 */
public interface BusinessChatExecutor {

    /**
     * 执行器模式键。
     *
     * @return 编排计划中的执行模式
     */
    BusinessChatMode executionMode();

    /**
     * 运行态 + 执行计划 -> 正文增量流。
     *
     * @param runtimeContext 本轮运行上下文
     * @param executionPlan 编排生成的执行计划
     * @return 模型或业务执行产生的正文增量流
     */
    Flux<String> execute(BusinessChatRuntimeContext runtimeContext, BusinessChatExecutionPlan executionPlan);
}
