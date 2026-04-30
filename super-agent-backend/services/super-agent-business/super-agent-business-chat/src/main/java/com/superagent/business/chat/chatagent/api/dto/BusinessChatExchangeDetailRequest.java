package com.superagent.business.chat.chatagent.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BusinessChatExchangeDetailRequest {

    @NotBlank(message = "conversationId must not be blank")
    private String conversationId;

    @NotBlank(message = "exchangeId must not be blank")
    private String exchangeId;
}
