package com.labmind.business.chat.chatagent.api.vo;

import java.math.BigDecimal;
import lombok.Data;

@Data
public class BusinessChatModelCallTraceVo {

    private String stageCode;

    private String stageName;

    private String provider;

    private String modelName;

    private String callType;

    private Integer inputTokens;

    private Integer outputTokens;

    private Integer totalTokens;

    private BigDecimal estimatedCost;

    private String currency;

    private Long durationMs;

    private String callState;

    private String errorMessage;
}
