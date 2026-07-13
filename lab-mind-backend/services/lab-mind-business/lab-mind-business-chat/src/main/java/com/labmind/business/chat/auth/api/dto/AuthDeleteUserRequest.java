package com.labmind.business.chat.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AuthDeleteUserRequest {

    @NotBlank(message = "userId must not be blank")
    private String userId;
}
