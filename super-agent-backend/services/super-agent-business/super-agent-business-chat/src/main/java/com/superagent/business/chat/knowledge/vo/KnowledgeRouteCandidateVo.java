package com.superagent.business.chat.knowledge.vo;

import lombok.Data;

@Data
public class KnowledgeRouteCandidateVo {

    private String documentId;

    private String documentName;

    private String scopeCode;

    private String scopeName;

    private String topicCode;

    private String topicName;

    private double score;

    private String hitReason;
}
