package com.superagent.business.chat.chatagent.service;

import com.superagent.business.chat.chatagent.api.dto.BusinessChatExchangeDetailRequest;
import com.superagent.business.chat.chatagent.api.dto.BusinessChatSessionDetailRequest;
import com.superagent.business.chat.chatagent.api.dto.BusinessChatSessionListRequest;
import com.superagent.business.chat.chatagent.api.vo.BusinessChatExchangeDetailVo;
import com.superagent.business.chat.chatagent.api.vo.BusinessChatSessionDetailVo;
import com.superagent.business.chat.chatagent.api.vo.BusinessChatSessionListPageVo;

/**
 * 会话查询服务。
 *
 * <p>本接口只负责把已归档或运行中的会话数据读出来，不推进对话执行状态。
 * 列表页读取会话维度的最近一轮摘要，详情页读取会话主记录、长期摘要和全部 exchange 明细。</p>
 */
public interface BusinessChatQueryService {

    /**
     * 分页查询会话列表。
     *
     * <p>输入的 chatMode、turnStatus 会被转换成数据库枚举码；输出的每条记录代表一个 conversationId，
     * 并携带该会话最新一轮 exchange 的问题、回答和状态。</p>
     *
     * @param request 列表筛选和分页条件
     * @return 会话列表分页结果
     */
    BusinessChatSessionListPageVo listSessionsPage(BusinessChatSessionListRequest request);

    /**
     * 查询单个会话详情。
     *
     * <p>以 conversationId 为唯一入口，返回会话当前模式、阶段、摘要和按时间排序的全部问答轮次，
     * 前端历史会话回放直接使用这份数据重建消息列表。</p>
     *
     * @param request 会话详情请求，必须包含 conversationId
     * @return 会话详情和完整 exchange 列表
     */
    BusinessChatSessionDetailVo getSession(BusinessChatSessionDetailRequest request);

    BusinessChatExchangeDetailVo getExchangeDetail(BusinessChatExchangeDetailRequest request);

    String getActiveConversationId(String workspaceId, String authSessionToken);
}
