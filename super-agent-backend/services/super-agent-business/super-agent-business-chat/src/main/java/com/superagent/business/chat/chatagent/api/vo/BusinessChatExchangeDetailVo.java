package com.superagent.business.chat.chatagent.api.vo;

import java.util.List;
import lombok.Data;

@Data
public class BusinessChatExchangeDetailVo {

    private String conversationId;

    private String exchangeId;

    private String userPrompt;

    private String replyContent;

    private String exchangeState;

    private String finishNote;

    private Long firstTokenLatencyMs;

    private Long totalLatencyMs;

    private BusinessChatExchangeUsageSummaryVo usageSummary;

    private List<BusinessChatExchangeTraceStageVo> stages;

    private List<BusinessChatModelCallTraceVo> modelCalls;

    private List<BusinessChatToolCallTraceVo> toolCalls;
}
