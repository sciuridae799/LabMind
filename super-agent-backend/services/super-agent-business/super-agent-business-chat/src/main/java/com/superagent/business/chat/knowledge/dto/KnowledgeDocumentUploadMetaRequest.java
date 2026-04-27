package com.superagent.business.chat.knowledge.dto;

import lombok.Data;

@Data
public class KnowledgeDocumentUploadMetaRequest {

    private String documentName;

    private String operatorId;

    private String knowledgeScopeCode;

    private String knowledgeScopeName;

    private String knowledgeTopicCode;

    private String knowledgeTopicName;

    private String businessCategory;

    private String documentTags;
}
