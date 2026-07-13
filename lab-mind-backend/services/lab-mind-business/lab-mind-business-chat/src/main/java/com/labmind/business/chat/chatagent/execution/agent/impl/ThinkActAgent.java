package com.labmind.business.chat.chatagent.execution.agent.impl;

import com.labmind.business.chat.chatagent.execution.agent.BusinessChatAgentType;
import com.labmind.business.chat.chatagent.execution.BusinessChatExecutorRegistry;
import org.springframework.stereotype.Service;

/**
 * 思考-行动 Agent。
 *
 * <p>该 Agent 承接当前文档问答和开放问答等需要按上下文组织回答的请求。
 * Agent 本身只声明业务角色，具体输入拼装、模型调用和流式输出仍由 executionMode 对应的执行器完成。</p>
 */
@Service
public class ThinkActAgent extends AbstractExecutorBackedBusinessChatAgent {

    public ThinkActAgent(BusinessChatExecutorRegistry executorRegistry) {
        super(executorRegistry);
    }

    @Override
    public BusinessChatAgentType agentType() {
        return BusinessChatAgentType.THINK_ACT;
    }
}
