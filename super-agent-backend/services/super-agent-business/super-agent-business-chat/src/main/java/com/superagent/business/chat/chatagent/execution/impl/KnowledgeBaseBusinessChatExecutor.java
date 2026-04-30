package com.superagent.business.chat.chatagent.execution.impl;

import com.superagent.business.chat.chatagent.execution.BusinessChatDynamicModelClient;
import com.superagent.business.chat.chatagent.orchestration.model.BusinessChatMode;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeBaseBusinessChatExecutor extends AbstractChatClientBusinessChatExecutor {

    public KnowledgeBaseBusinessChatExecutor(BusinessChatDynamicModelClient modelClient) {
        super(modelClient);
    }

    @Override
    public BusinessChatMode executionMode() {
        return BusinessChatMode.KNOWLEDGE_BASE;
    }
}
