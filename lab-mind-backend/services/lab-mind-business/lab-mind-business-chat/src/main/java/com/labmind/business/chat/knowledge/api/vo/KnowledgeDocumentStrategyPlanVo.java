package com.labmind.business.chat.knowledge.api.vo;

import java.util.List;
import lombok.Data;

@Data
public class KnowledgeDocumentStrategyPlanVo {

    private String planId;

    private String documentId;

    private Integer planVersion;

    private String planSource;

    private String planStatus;

    private String strategySnapshot;

    private String recommendReason;

    private List<KnowledgeDocumentStrategyStepVo> steps;
}
