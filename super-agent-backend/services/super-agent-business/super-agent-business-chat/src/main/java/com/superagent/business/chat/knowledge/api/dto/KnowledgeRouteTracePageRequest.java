package com.superagent.business.chat.knowledge.api.dto;

import lombok.Data;

@Data
public class KnowledgeRouteTracePageRequest {

    private String keyword;

    private String workspaceId;

    private String pageNo = "1";

    private String pageSize = "20";
}
