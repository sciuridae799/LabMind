package com.labmind.business.chat.chatagent.execution;

import com.labmind.business.chat.chatagent.orchestration.model.BusinessChatMode;

/**
 * 对话执行器注册表。
 *
 * <p>executionMode -> BusinessChatExecutor。</p>
 */
public interface BusinessChatExecutorRegistry {

    /**
     * executionMode -> 执行器。
     *
     * @param executionMode 编排计划中的执行模式
     * @return 负责该模式的执行器
     */
    BusinessChatExecutor getRequiredExecutor(BusinessChatMode executionMode);
}
