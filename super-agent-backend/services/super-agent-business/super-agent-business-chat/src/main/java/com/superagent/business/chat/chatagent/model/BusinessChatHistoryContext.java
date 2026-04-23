package com.superagent.business.chat.chatagent.model;

import java.util.List;

/**
 * 会话历史上下文。
 *
 * <p>编排器把长期摘要和最近对话窗口合并成 contextText，后续问题改写和模型回答都消费这份上下文。</p>
 */
public record BusinessChatHistoryContext(
        String memorySummary,
        List<BusinessChatRecentExchange> recentExchangeList,
        String contextText) {

    public int recentExchangeCount() {
        return recentExchangeList.size();
    }
}
