package com.labmind.business.chat.chatagent.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labmind.business.chat.chatagent.persistence.data.BusinessChatMemorySummaryData;
import org.apache.ibatis.annotations.Mapper;

/**
 * 会话长期摘要表访问接口。
 *
 * <p>摘要按 conversationId 维护，用来记录当前会话已覆盖到哪一轮 exchange 以及最近上下文概览。</p>
 */
@Mapper
public interface BusinessChatMemorySummaryMapper extends BaseMapper<BusinessChatMemorySummaryData> {
}
