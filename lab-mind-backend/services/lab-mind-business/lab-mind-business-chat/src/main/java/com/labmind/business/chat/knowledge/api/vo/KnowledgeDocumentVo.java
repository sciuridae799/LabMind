package com.labmind.business.chat.knowledge.api.vo;

import lombok.Data;

@Data
public class KnowledgeDocumentVo {

    private String documentId;

    private String documentName;

    private String originalFileName;

    private String knowledgeScopeCode;

    private String knowledgeScopeName;

    private String businessCategory;

    private String documentTags;

    private String parseStatus;

    private String strategyStatus;

    private String indexStatus;

    private String createTime;
}
