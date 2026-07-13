package com.labmind.business.chat.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AuthLoginRequest {

    @NotBlank(message = "account must not be blank")
    private String account;

    @NotBlank(message = "password must not be blank")
    private String password;
}
