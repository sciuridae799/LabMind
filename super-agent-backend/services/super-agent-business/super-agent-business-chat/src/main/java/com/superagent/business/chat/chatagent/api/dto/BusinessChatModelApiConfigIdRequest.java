package com.superagent.business.chat.chatagent.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BusinessChatModelApiConfigIdRequest {

    @NotBlank(message = "id must not be blank")
    private String id;
}
