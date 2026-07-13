package com.superagent.business.chat.chatagent.execution;

import com.superagent.business.chat.chatagent.orchestration.model.BusinessChatExecutionPlan;
import com.superagent.business.chat.chatagent.execution.model.BusinessChatModelApiConfigSnapshot;
import com.superagent.business.chat.chatagent.runtime.BusinessChatRuntimeContext;
import reactor.core.publisher.Flux;

public interface BusinessChatDynamicModelClient {

    Flux<String> stream(BusinessChatModelApiConfigSnapshot modelConfig, BusinessChatExecutionPlan executionPlan);

    Flux<String> stream(BusinessChatRuntimeContext runtimeContext, BusinessChatExecutionPlan executionPlan);

    String call(BusinessChatModelApiConfigSnapshot modelConfig, String systemPrompt, String userMessage);

    String call(
            BusinessChatRuntimeContext runtimeContext,
            BusinessChatModelApiConfigSnapshot modelConfig,
            String systemPrompt,
            String userMessage);

    String callJsonObject(
            BusinessChatRuntimeContext runtimeContext,
            BusinessChatModelApiConfigSnapshot modelConfig,
            String systemPrompt,
            String userMessage);
}
