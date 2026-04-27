package com.superagent.business.chat.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class KnowledgeRoutePreviewRequest {

    @NotBlank(message = "question must not be blank")
    private String question;

    private String limit = "10";
}
