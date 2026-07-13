package com.labmind.business.chat.knowledge.api.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class KnowledgeRoutePreviewRequest {

    @NotBlank(message = "question must not be blank")
    private String question;

    private String workspaceId;

    private String limit = "10";
}
