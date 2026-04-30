package com.superagent.business.chat.chatagent.runtime;

import com.superagent.business.chat.chatagent.runtime.BusinessChatTaskInfo;
import com.superagent.business.chat.chatagent.service.BusinessChatErrorCode;
import com.superagent.common.frame.exception.BaseException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Sinks;

/**
 * 基于内存的运行态注册表。
 *
 * <p>它只解决当前 JVM 内 conversationId 到 RuntimeContext 的定位和冲突检测，不能替代 Redis 租约的跨实例互斥。</p>
 */
@Service
public class InMemoryBusinessChatRuntimeRegistry implements BusinessChatRuntimeRegistry {

    private final ConcurrentMap<String, BusinessChatRuntimeContext> runtimeContextMap = new ConcurrentHashMap<>();

    @Override
    public BusinessChatRuntimeContext register(BusinessChatTaskInfo taskInfo) {
        // JVM 内注册表只承载本进程执行期上下文；跨进程互斥由 Redis 会话租约保证。
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
