package com.superagent.business.chat.chatagent.api.vo;

import java.util.List;
import lombok.Data;

@Data
public class BusinessChatSessionDetailVo {

    private String conversationId;

    private String title;

    private String chatMode;

    private String dialogueStage;

    private String selectedDocumentId;

    private String selectedDocumentName;

    private String summaryText;

    private Object summaryJson;

    private List<BusinessChatSessionExchangeVo> exchanges;
}
