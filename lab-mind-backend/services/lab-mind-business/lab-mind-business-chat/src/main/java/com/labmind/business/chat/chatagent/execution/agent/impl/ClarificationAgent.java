package com.labmind.business.chat.chatagent.execution.agent.impl;

import com.labmind.business.chat.chatagent.execution.agent.BusinessChatAgent;
import com.labmind.business.chat.chatagent.execution.agent.BusinessChatAgentType;
import com.labmind.business.chat.chatagent.orchestration.model.BusinessChatExecutionPlan;
import com.labmind.business.chat.chatagent.runtime.BusinessChatRuntimeContext;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

/**
 * 歧义澄清 Agent。
 *
 * <p>当编排层无法得到足够确定的执行计划时，会生成 clarificationPlan 并选择该 Agent。
 * 这里的职责不是继续调用模型生成答案，而是把编排阶段已经确定的澄清问题直接返回给用户。</p>
 */
@Service
public class ClarificationAgent implements BusinessChatAgent {

    @Override
    public BusinessChatAgentType agentType() {
        return BusinessChatAgentType.CLARIFICATION;
    }

    @Override
    public Flux<String> execute(BusinessChatRuntimeContext runtimeContext, BusinessChatExecutionPlan executionPlan) {
        /*
         * CLARIFICATION Agent 必须依赖显式澄清计划。
         * 如果计划缺失或未要求澄清，说明 agentType 与 executionPlan 不一致，应直接失败。
         */
        if (executionPlan.clarificationPlan() == null || !executionPlan.clarificationPlan().required()) {
            throw new IllegalStateException("clarification plan is required for CLARIFICATION agent.");
        }
        String reply = executionPlan.clarificationPlan().reply();
        /*
         * 澄清回复是用户实际看到的输出，不能为空白文本。
         */
        if (!StringUtils.hasText(reply)) {
            throw new IllegalStateException("clarification reply must not be blank.");
        }
        return Flux.just(reply);
    }
}
