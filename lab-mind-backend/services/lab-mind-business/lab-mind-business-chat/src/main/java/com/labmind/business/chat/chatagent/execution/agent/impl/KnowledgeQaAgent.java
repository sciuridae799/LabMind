package com.labmind.business.chat.chatagent.execution.agent.impl;

import com.labmind.business.chat.chatagent.execution.agent.BusinessChatAgentType;
import com.labmind.business.chat.chatagent.execution.BusinessChatExecutorRegistry;
import org.springframework.stereotype.Service;

/**
 * 知识库问答 Agent。
 *
 * <p>该 Agent 承接已经被编排层判定为知识库问答的请求。
 * 它不重新选择知识库、不重新判断意图，只沿用执行计划中的路由结果、召回证据和 executionMode，
 * 再交给执行器完成最终回答生成。</p>
 */
@Service
public class KnowledgeQaAgent extends AbstractExecutorBackedBusinessChatAgent {

    public KnowledgeQaAgent(BusinessChatExecutorRegistry executorRegistry) {
        super(executorRegistry);
    }

    @Override
    public BusinessChatAgentType agentType() {
        return BusinessChatAgentType.KNOWLEDGE_QA;
    }
}
