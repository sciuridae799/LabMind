package com.superagent.business.chat.chatagent.agent.impl;

import com.superagent.business.chat.chatagent.agent.BusinessChatAgentType;
import com.superagent.business.chat.chatagent.execution.BusinessChatExecutorRegistry;
import org.springframework.stereotype.Service;

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
