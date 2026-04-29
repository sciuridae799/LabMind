package com.superagent.business.chat.knowledge.model;

/**
 * 写入 Neo4j 文档结构图的节点快照。
 *
 * <p>该对象来自 MySQL 结构节点表，图数据库只消费这个稳定快照，不重新解析正文。</p>
 */
public record KnowledgeDocumentStructureGraphNode(
        Long nodeId,
        Integer nodeNo,
        Integer nodeType,
        Long parentNodeId,
        Long prevSiblingNodeId,
        Long nextSiblingNodeId,
        Integer depth,
        String nodeCode,
        String title,
        String anchorText,
        String canonicalPath,
        String sectionPath,
        String contentText,
        Integer itemIndex) {
}
