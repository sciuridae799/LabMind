package com.superagent.business.chat.chatagent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BusinessChatModelApiConfigMoveRequest {

    @NotBlank(message = "id must not be blank")
    private String id;

    @NotBlank(message = "direction must not be blank")
    private String direction;
}
