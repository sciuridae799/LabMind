package com.superagent.business.chat.knowledge.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.superagent.common.web.database.BaseTableData;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("super_agent_knowledge_route_trace")
public class KnowledgeRouteTraceData extends BaseTableData {

    @TableId(type = IdType.INPUT)
    private Long id;

    private String traceId;

    private String conversationId;

    private Long exchangeId;

    private String question;

    private String rewrittenQuestion;

    private String intentType;

    private String selectedScopeCode;

    private String selectedTopicCode;

    private String selectedDocumentIds;

    private String routeResultJson;

    private Long userSelectedDocumentId;

    private Long routeTopDocumentId;

    private Integer hitSelectedDocument;

    private Double confidence;

    private String routeStatus;

    private String routeMode;

    private Integer status;
}
