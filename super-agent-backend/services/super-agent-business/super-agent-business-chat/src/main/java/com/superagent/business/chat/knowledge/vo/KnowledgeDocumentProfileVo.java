package com.superagent.business.chat.knowledge.vo;

import java.util.List;
import lombok.Data;

@Data
public class KnowledgeDocumentProfileVo {

    private String documentId;

    private String scopeCode;

    private String topicCode;

    private String summaryText;

    private List<String> answerableQuestions;

    private List<String> unanswerableQuestions;

    private List<String> businessEntities;

    private List<String> terms;

    private List<String> questionPatterns;
}
