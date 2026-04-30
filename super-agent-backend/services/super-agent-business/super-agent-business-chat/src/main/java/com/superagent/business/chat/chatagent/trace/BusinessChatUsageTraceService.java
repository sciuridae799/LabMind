package com.superagent.business.chat.chatagent.trace;

import com.superagent.business.chat.chatagent.execution.model.BusinessChatModelApiConfigSnapshot;
import com.superagent.business.chat.chatagent.runtime.BusinessChatRuntimeContext;
import org.springframework.ai.chat.metadata.Usage;

public interface BusinessChatUsageTraceService {

    Long startModelCall(
            BusinessChatRuntimeContext runtimeContext,
            BusinessChatModelApiConfigSnapshot modelConfig,
            String callType);

    void completeModelCall(Long traceId, Usage usage);

    void failModelCall(Long traceId, Throwable error);

    Long startToolCall(BusinessChatRuntimeContext runtimeContext, String toolName);

    void completeToolCall(Long traceId);

    void failToolCall(Long traceId, Throwable error);
}
