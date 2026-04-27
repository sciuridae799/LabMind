package com.superagent.business.chat.knowledge.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class KnowledgeDocumentIdRequest {

    @NotBlank(message = "documentId must not be blank")
    private String documentId;
}
