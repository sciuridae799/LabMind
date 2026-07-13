package com.labmind.business.chat.chatagent.execution.impl;

import com.labmind.business.chat.chatagent.execution.BusinessChatDynamicModelClient;
import com.labmind.business.chat.chatagent.orchestration.model.BusinessChatMode;
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
