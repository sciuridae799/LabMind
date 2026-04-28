package com.superagent.business.chat.knowledge.vo;

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

    private double termScore;

    private double patternScore;

    private List<String> hitTerms;

    private List<String> matchedPatterns;

    private String hitReason;
}
