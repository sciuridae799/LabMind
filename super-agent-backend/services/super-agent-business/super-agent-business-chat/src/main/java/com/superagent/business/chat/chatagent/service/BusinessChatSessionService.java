package com.superagent.business.chat.chatagent.service;

import com.superagent.business.chat.chatagent.api.dto.BusinessChatDeleteSessionRequest;

/**
 * 会话生命周期服务。
 *
 * <p>本接口处理会话级操作，不直接生成回答。会话级操作必须和流式生成共用同一套会话锁，
 * 保证删除、运行、归档这些状态变化不会互相覆盖。</p>
 */
public interface BusinessChatSessionService {

    /**
     * 删除整条会话归档。
     *
     * <p>删除对象是 conversationId 下的会话主记录、问答轮次、摘要、阶段明细、模型/工具调用轨迹
     * 和 Graph checkpoint thread。
     * 如果会话正在生成，本方法应拒绝删除，避免运行中的 exchange 被软删后仍继续写回。</p>
     *
     * @param request 删除请求，必须包含 conversationId
     */
    void deleteSession(BusinessChatDeleteSessionRequest request);
}
