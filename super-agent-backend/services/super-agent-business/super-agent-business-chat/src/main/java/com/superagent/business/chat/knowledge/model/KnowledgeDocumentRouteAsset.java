package com.superagent.business.chat.knowledge.model;

import java.util.List;

/**
 * 可写入知识路由图的文档资产。
 *
 * <p>由文档解析和画像生成链路产出，包含路由所需的知识域、专题、摘要、术语和问题模式。</p>
 */
public record KnowledgeDocumentRouteAsset(
        Long documentId,
        String documentName,
        String scopeCode,
        String scopeName,
        String topicCode,
        String topicName,
        String summary,
        List<String> terms,
        List<String> questionPatterns) {
}
