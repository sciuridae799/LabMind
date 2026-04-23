package com.superagent.business.chat.chatagent.execution;

import com.superagent.business.chat.chatagent.model.BusinessChatExecutionPlan;
import com.superagent.business.chat.chatagent.model.BusinessChatModelApiConfigSnapshot;
import reactor.core.publisher.Flux;

public interface BusinessChatDynamicModelClient {

    Flux<String> stream(BusinessChatModelApiConfigSnapshot modelConfig, BusinessChatExecutionPlan executionPlan);

    String call(BusinessChatModelApiConfigSnapshot modelConfig, String systemPrompt, String userMessage);
}
