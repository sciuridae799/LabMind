package com.superagent.business.chat.chatagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.superagent.business.chat.chatagent.data.BusinessChatExchangeTraceStageData;
import org.apache.ibatis.annotations.Mapper;

/**
 * 问答阶段明细表访问接口。
 *
 * <p>阶段明细用于记录单轮 exchange 在各执行阶段的状态、耗时和调试信息，
 * 会话删除时需要和会话主记录、问答轮次、摘要一起按 conversationId 软删。</p>
 */
@Mapper
public interface BusinessChatExchangeTraceStageMapper extends BaseMapper<BusinessChatExchangeTraceStageData> {
}
