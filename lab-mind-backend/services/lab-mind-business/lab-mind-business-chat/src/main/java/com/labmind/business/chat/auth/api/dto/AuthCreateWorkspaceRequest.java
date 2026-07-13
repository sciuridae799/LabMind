package com.labmind.business.chat.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AuthCreateWorkspaceRequest {

    private String workspaceCode;

    @NotBlank(message = "workspaceName must not be blank")
    private String workspaceName;
}
