package com.superagent.business.chat.chatagent.execution.impl;

import com.superagent.business.chat.chatagent.execution.BusinessChatDynamicModelClient;
import com.superagent.business.chat.chatagent.model.BusinessChatMode;
import org.springframework.stereotype.Service;

@Service
public class OpenEndedBusinessChatExecutor extends AbstractChatClientBusinessChatExecutor {

    public OpenEndedBusinessChatExecutor(BusinessChatDynamicModelClient modelClient) {
        super(modelClient);
    }

    @Override
    public BusinessChatMode executionMode() {
        return BusinessChatMode.OPEN_ENDED;
    }
}
