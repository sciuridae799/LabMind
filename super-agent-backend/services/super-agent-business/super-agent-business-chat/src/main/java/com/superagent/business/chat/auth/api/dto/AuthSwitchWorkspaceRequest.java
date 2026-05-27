package com.superagent.business.chat.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AuthSwitchWorkspaceRequest {

    @NotBlank(message = "workspaceId must not be blank")
    private String workspaceId;
}
