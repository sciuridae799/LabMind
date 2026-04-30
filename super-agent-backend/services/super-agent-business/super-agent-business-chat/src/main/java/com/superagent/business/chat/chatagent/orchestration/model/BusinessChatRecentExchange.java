package com.superagent.business.chat.chatagent.orchestration.model;

import java.time.LocalDateTime;

/**
 * 最近对话窗口中的一轮已完成 exchange。
 *
 * <p>这里只保留构建历史上下文需要的业务字段：用户问题、助手回答和该轮发生时间。</p>
 */
public record BusinessChatRecentExchange(
        String userPrompt,
        String replyContent,
        LocalDateTime createTime) {
}
