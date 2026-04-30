package com.superagent.business.chat.chatagent.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BusinessChatStreamRequest {

    @NotBlank(message = "question must not be blank")
    private String question;

    @Size(max = 64, message = "conversationId length must be less than or equal to 64")
    private String conversationId;

    @NotBlank(message = "chatMode must not be blank")
    private String chatMode;

    @NotBlank(message = "modelConfigId must not be blank")
    private String modelConfigId;

    private String selectedDocumentId;
}
