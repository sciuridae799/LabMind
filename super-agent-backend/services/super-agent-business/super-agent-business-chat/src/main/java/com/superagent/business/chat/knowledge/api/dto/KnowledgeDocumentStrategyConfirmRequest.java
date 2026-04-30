package com.superagent.business.chat.knowledge.api.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.List;
import lombok.Data;

@Data
public class KnowledgeDocumentStrategyConfirmRequest {

    @NotBlank(message = "documentId must not be blank")
    private String documentId;

    private List<Integer> strategyTypes;

    private String adjustNote;

    private String operatorId;
}
