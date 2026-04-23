package com.superagent.business.chat.chatagent.vo;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class BusinessChatSessionExchangeVo {

    private Long exchangeId;

    private String userPrompt;

    private String replyContent;

    private List<String> sourceSnapshotList;

    private List<String> followUpSuggestionList;

    private List<String> toolTraceList;

    private String exchangeState;

    private String finishNote;

    private Long firstTokenLatencyMs;

    private Long totalLatencyMs;

    private LocalDateTime createTime;
}
