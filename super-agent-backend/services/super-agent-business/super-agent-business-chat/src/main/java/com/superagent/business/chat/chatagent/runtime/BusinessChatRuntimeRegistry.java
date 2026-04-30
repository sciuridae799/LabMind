package com.superagent.business.chat.chatagent.runtime;

import com.superagent.business.chat.chatagent.runtime.BusinessChatTaskInfo;

/**
 * 运行中会话注册表。
 *
 * <p>注册表只保存当前 JVM 内正在执行的一轮对话上下文。</p>
 */
public interface BusinessChatRuntimeRegistry {

    /**
     * 注册一轮正在执行的会话上下文。
     *
     * @param taskInfo 本轮任务快照
     * @return 新建的运行上下文
     */
    BusinessChatRuntimeContext register(BusinessChatTaskInfo taskInfo);

    /**
     * 注销已结束的会话上下文。
     *
     * <p>对话成功、失败或启动回滚后都必须注销。</p>
     *
     * @param conversationId 会话编号
     */
    void unregister(String conversationId);
}
