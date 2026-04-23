package com.superagent.business.chat.chatagent.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class BusinessChatSessionListRequest {

    private String keyword;

    @NotBlank(message = "chatMode must not be blank")
    private String chatMode;

    @NotBlank(message = "turnStatus must not be blank")
    private String turnStatus;

    @NotBlank(message = "pageNo must not be blank")
    private String pageNo;

    @NotBlank(message = "pageSize must not be blank")
    private String pageSize;
}
