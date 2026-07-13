package com.labmind.business.chat.knowledge.api.vo;

import lombok.Data;

@Data
public class KnowledgeDocumentStrategyStepVo {

    private String stepId;

    private Integer stepNo;

    private String pipelineType;

    private Integer strategyType;

    private Integer strategyRole;

    private String executeStatus;

    private String recommendReason;
}
