package com.superagent.business.chat.knowledge.dto;

import lombok.Data;

@Data
public class KnowledgeRouteAssetPageRequest {

    private String keyword;

    private String knowledgeScopeCode;

    private String pageNo = "1";

    private String pageSize = "20";
}
