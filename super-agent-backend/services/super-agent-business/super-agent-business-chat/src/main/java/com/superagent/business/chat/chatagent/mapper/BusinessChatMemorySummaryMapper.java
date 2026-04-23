package com.superagent.business.chat.chatagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.superagent.business.chat.chatagent.data.BusinessChatMemorySummaryData;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会话长期摘要表访问接口。
 *
 * <p>摘要按 conversationId 维护，用来记录当前会话已覆盖到哪一轮 exchange 以及最近上下文概览。</p>
 */
@Mapper
public interface BusinessChatMemorySummaryMapper extends BaseMapper<BusinessChatMemorySummaryData> {
}
