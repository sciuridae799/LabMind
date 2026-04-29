package com.superagent.business.chat.knowledge.model;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 知识路由追踪查询行。
 *
 * <p>Mapper 从真实路由 trace 表中取出路由结果快照，Service 再转换为前端复盘视图。</p>
 */
@Data
public class KnowledgeRouteTraceRow {

    private String conversationId;

    private Long exchangeId;

    private String question;

    private String rewrittenQuestion;

    private String routeResultJson;

    private String routeMode;

    private String routeStatus;

    private Double confidence;

    private Integer hitSelectedDocument;

    private LocalDateTime createTime;
}
