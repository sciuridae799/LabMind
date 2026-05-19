package com.superagent.business.chat.chatagent.api.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class BusinessChatExchangeTraceStageVo {

    private String stageCode;

    private String stageName;

    private Integer stageOrder;

    private Integer stageLevel;

    private String parentStageId;

    private String stageState;

    private Long durationMs;

    private String summaryText;

    private String errorMessage;

    private Object snapshot;

    private LocalDateTime startTime;

    private LocalDateTime endTime;
}
