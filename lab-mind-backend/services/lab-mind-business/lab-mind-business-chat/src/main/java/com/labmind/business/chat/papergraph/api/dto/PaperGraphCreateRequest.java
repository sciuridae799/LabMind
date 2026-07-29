package com.labmind.business.chat.papergraph.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PaperGraphCreateRequest {

    @NotBlank(message = "name must not be blank")
    @Size(max = 160, message = "name must not exceed 160 characters")
    private String name;

    @Size(max = 2000, message = "description must not exceed 2000 characters")
    private String description;
}
