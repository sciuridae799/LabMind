package com.superagent.business.chat.chatagent.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.superagent.business.chat.chatagent.data.BusinessChatExchangeData;
import org.apache.ibatis.annotations.Mapper;

/**
 * 问答轮次表访问接口。
 *
 * <p>每条 exchange 对应会话中的一轮用户问题和助手回答，承载正文、追问、工具痕迹、状态和耗时等归档结果。</p>
 */
@Mapper
public interface BusinessChatExchangeMapper extends BaseMapper<BusinessChatExchangeData> {
}
