package com.labmind.business.chat.knowledge.api.vo;

import java.util.List;
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

    private double semanticScore;

    private double lexicalScore;

    private double termScore;

    private double patternScore;

    private List<String> hitTerms;

    private List<String> matchedPatterns;

    private String hitReason;
}
