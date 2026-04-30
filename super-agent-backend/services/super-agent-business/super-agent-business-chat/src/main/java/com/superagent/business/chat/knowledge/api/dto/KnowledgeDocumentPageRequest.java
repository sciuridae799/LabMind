package com.superagent.business.chat.knowledge.api.dto;

import lombok.Data;

@Data
public class KnowledgeDocumentPageRequest {

    private String keyword;

    private String knowledgeScopeCode;

    private String pageNo = "1";

    private String pageSize = "20";
}
