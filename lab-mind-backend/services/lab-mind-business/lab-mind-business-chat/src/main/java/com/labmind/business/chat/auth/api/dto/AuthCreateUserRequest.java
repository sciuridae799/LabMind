package com.labmind.business.chat.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import lombok.Data;

@Data
public class AuthCreateUserRequest {

    @NotBlank(message = "account must not be blank")
    private String account;

    @NotBlank(message = "displayName must not be blank")
    private String displayName;

    @NotBlank(message = "password must not be blank")
    private String password;

    @NotBlank(message = "role must not be blank")
    private String role;

    @NotEmpty(message = "workspaceIds must not be empty")
    private List<String> workspaceIds;
}
