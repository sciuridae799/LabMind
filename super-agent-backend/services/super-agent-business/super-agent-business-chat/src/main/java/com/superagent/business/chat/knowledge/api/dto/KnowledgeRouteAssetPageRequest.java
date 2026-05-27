package com.superagent.business.chat.knowledge.api.dto;

import lombok.Data;

@Data
public class KnowledgeRouteAssetPageRequest {

    private String keyword;

    private String workspaceId;

    private String knowledgeScopeCode;

    private String pageNo = "1";

    private String pageSize = "20";
}
