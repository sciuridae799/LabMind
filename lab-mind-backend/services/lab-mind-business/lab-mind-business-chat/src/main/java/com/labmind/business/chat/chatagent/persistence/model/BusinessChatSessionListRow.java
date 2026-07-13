package com.labmind.business.chat.chatagent.persistence.model;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class BusinessChatSessionListRow {

    private String conversationId;

    private String title;

    private Integer chatModeCode;

    private Integer turnStatusCode;

    private Long lastExchangeId;

    private String lastQuestion;

    private String lastReply;

    private LocalDateTime updateTime;
}
