package com.superagent.business.chat.chatagent.vo;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class BusinessChatSessionListItemVo {

    private String conversationId;

    private String title;

    private String chatMode;

    private String turnStatus;

    private Long lastExchangeId;

    private String lastQuestion;

    private String lastReply;

    private LocalDateTime updateTime;
}
