package com.labmind.business.chat.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AuthUpdateWorkspaceRequest {

    @NotBlank(message = "workspaceId must not be blank")
    private String workspaceId;

    @NotBlank(message = "workspaceName must not be blank")
    private String workspaceName;
}
