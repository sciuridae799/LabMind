package com.superagent.business.chat.knowledge.dto;

import lombok.Data;

@Data
public class KnowledgeRouteTracePageRequest {

    private String keyword;

    private String pageNo = "1";

    private String pageSize = "20";
}
