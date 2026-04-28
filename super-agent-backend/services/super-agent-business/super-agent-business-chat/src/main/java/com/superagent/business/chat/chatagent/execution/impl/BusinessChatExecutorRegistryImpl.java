package com.superagent.business.chat.chatagent.execution.impl;

import com.superagent.business.chat.chatagent.model.BusinessChatMode;
import com.superagent.business.chat.chatagent.service.BusinessChatErrorCode;
import com.superagent.business.chat.chatagent.execution.BusinessChatExecutor;
import com.superagent.business.chat.chatagent.execution.BusinessChatExecutorRegistry;
import com.superagent.common.frame.exception.BaseException;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/**
 * 问答执行器注册表实现。
 *
 * <p>启动时按 BusinessChatMode 建立唯一映射，编排计划确定模式后通过这里找到实际正文生成执行器。</p>
 */
@Service
public class BusinessChatExecutorRegistryImpl implements BusinessChatExecutorRegistry {

    private final Map<BusinessChatMode, BusinessChatExecutor> executorMap;

    public BusinessChatExecutorRegistryImpl(List<BusinessChatExecutor> executorList) {
        EnumMap<BusinessChatMode, BusinessChatExecutor> registeredExecutorMap = new EnumMap<>(BusinessChatMode.class);
        for (BusinessChatExecutor executor : executorList) {
            BusinessChatMode executionMode = requireExecutionMode(executor);
            BusinessChatExecutor existingExecutor = registeredExecutorMap.putIfAbsent(executionMode, executor);
            if (existingExecutor != null) {
                throw new BaseException(
                        BusinessChatErrorCode.CHAT_EXECUTOR_REGISTRATION_INVALID,
                        "Duplicate executor for execution mode: " + executionMode.getValue());
            }
        }
        this.executorMap = Map.copyOf(registeredExecutorMap);
    }

    @Override
    public BusinessChatExecutor getRequiredExecutor(BusinessChatMode executionMode) {
        if (executionMode == null) {
            throw new BaseException(
                    BusinessChatErrorCode.CHAT_EXECUTOR_NOT_FOUND,
                    "execution mode is required");
        }
        BusinessChatExecutor executor = executorMap.get(executionMode);
        if (executor == null) {
            throw new BaseException(
                    BusinessChatErrorCode.CHAT_EXECUTOR_NOT_FOUND,
                    "No executor supports execution mode: " + executionMode.getValue());
        }
        return executor;
    }

    private BusinessChatMode requireExecutionMode(BusinessChatExecutor executor) {
        BusinessChatMode executionMode = executor.executionMode();
        if (executionMode == null) {
            throw new BaseException(
                    BusinessChatErrorCode.CHAT_EXECUTOR_REGISTRATION_INVALID,
                    "executor execution mode is required: " + executor.getClass().getName());
        }
        return executionMode;
    }
}
