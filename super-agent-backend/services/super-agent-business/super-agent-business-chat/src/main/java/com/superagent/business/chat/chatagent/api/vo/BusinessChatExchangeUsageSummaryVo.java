package com.superagent.business.chat.chatagent.api.vo;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class BusinessChatExchangeUsageSummaryVo {

    private Integer inputTokens;

    private Integer outputTokens;

    private Integer totalTokens;

    private BigDecimal estimatedCost;

    private String currency;

    private Integer modelCallCount;

    private Integer modelCallLimit;

    private Integer toolCallCount;

    private Integer toolCallLimit;

    private boolean limitTriggered;

    private String limitTriggerReason;
}
