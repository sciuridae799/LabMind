package com.labmind.business.chat.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.Data;

@Data
public class AuthUpdateUserRequest {

    @NotBlank(message = "userId must not be blank")
    private String userId;

    @NotBlank(message = "displayName must not be blank")
    private String displayName;

    @NotBlank(message = "role must not be blank")
    private String role;

    @NotNull(message = "enabled must not be null")
    private Boolean enabled;

    @NotEmpty(message = "workspaceIds must not be empty")
    private List<String> workspaceIds;
}
