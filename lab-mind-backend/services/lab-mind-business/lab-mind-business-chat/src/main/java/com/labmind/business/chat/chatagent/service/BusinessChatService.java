package com.labmind.business.chat.chatagent.service;

import com.labmind.business.chat.chatagent.api.dto.BusinessChatStreamRequest;
import com.labmind.business.chat.chatagent.api.vo.BusinessChatStreamEvent;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

/**
 * 对话流式生成入口。
 *
 * <p>调用方提交用户问题、可选会话编号和聊天模式后，本接口负责启动一轮完整问答：
 * 创建本轮运行记录、驱动智能体执行，并把正文增量、补充信息、推荐追问和最终状态通过 SSE 返回给前端。</p>
 */
public interface BusinessChatService {

    /**
     * 发起一轮流式对话。
     *
     * <p>输入是本轮用户问题和会话上下文；输出是按业务事件拆分的 SSE 流。
     * 同一个 conversationId 同一时间只能有一轮执行，运行中会话会返回拒绝事件。</p>
     *
     * @param request 本轮对话请求，包含 question、chatMode；conversationId 为空时后端创建新会话编号
     * @return 前端持续消费的业务事件流
     */
    Flux<ServerSentEvent<BusinessChatStreamEvent>> streamChat(BusinessChatStreamRequest request);
}
