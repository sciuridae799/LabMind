package com.superagent.business.chat.knowledge.model;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 知识路由追踪查询行。
 *
 * <p>Mapper 从已归档 exchange 中取出问题和 debugTraceJson，Service 再解析其中的执行计划供前端复盘。</p>
 */
@Data
public class KnowledgeRouteTraceRow {

    private String conversationId;

    private Long exchangeId;

    private String question;

    private String debugTraceJson;

    private LocalDateTime createTime;
}
