package com.superagent.business.chat.chatagent.runtime;

import com.superagent.business.chat.chatagent.model.BusinessChatTaskInfo;
import com.superagent.business.chat.chatagent.service.BusinessChatErrorCode;
import com.superagent.common.frame.exception.BaseException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Sinks;

@Service
public class InMemoryBusinessChatRuntimeRegistry implements BusinessChatRuntimeRegistry {

    private final ConcurrentMap<String, BusinessChatRuntimeContext> runtimeContextMap = new ConcurrentHashMap<>();

    @Override
    public BusinessChatRuntimeContext register(BusinessChatTaskInfo taskInfo) {
        BusinessChatRuntimeContext runtimeContext =
                new BusinessChatRuntimeContext(taskInfo, Sinks.many().unicast().onBackpressureBuffer());
        BusinessChatRuntimeContext existing = runtimeContextMap.putIfAbsent(taskInfo.conversationId(), runtimeContext);
        if (existing != null) {
            throw new BaseException(
                    BusinessChatErrorCode.CHAT_RUNTIME_CONFLICT,
                    "conversation runtime already exists: " + taskInfo.conversationId());
        }
        return runtimeContext;
    }

    @Override
    public void unregister(String conversationId) {
        runtimeContextMap.remove(conversationId);
    }
}
