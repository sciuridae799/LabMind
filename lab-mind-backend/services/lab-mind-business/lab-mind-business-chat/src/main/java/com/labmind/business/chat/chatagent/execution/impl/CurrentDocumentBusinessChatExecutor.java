package com.labmind.business.chat.chatagent.execution.impl;

import com.labmind.business.chat.chatagent.execution.BusinessChatDynamicModelClient;
import com.labmind.business.chat.chatagent.orchestration.model.BusinessChatMode;
import org.springframework.stereotype.Service;

@Service
public class CurrentDocumentBusinessChatExecutor extends AbstractChatClientBusinessChatExecutor {

    public CurrentDocumentBusinessChatExecutor(BusinessChatDynamicModelClient modelClient) {
        super(modelClient);
    }

    @Override
    public BusinessChatMode executionMode() {
        return BusinessChatMode.CURRENT_DOCUMENT;
    }
}
