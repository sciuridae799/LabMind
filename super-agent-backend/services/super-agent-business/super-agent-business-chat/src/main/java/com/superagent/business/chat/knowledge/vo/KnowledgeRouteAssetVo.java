package com.superagent.business.chat.knowledge.vo;

import java.util.List;
import lombok.Data;

@Data
public class KnowledgeRouteAssetVo {

    private String documentId;

    private String documentName;

    private String originalFileName;

    private String scopeCode;

    private String scopeName;

    private String topicCode;

    private String topicName;

    private String summaryText;

    private List<String> terms;

    private List<String> questionPatterns;

    private String routeStatus;

    private String updateTime;
}
