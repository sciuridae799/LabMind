package com.superagent.business.chat.chatagent.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class BusinessChatSessionDetailRequest {

    @NotBlank(message = "conversationId must not be blank")
    @Size(max = 64, message = "conversationId length must be less than or equal to 64")
    private String conversationId;
}
