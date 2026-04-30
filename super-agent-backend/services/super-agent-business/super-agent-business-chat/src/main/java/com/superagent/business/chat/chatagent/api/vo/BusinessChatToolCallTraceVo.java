package com.superagent.business.chat.chatagent.api.vo;

import lombok.Data;

@Data
public class BusinessChatToolCallTraceVo {

    private String toolName;

    private String callState;

    private Long durationMs;

    private String errorMessage;
}
