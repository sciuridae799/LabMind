package com.labmind.business.chat.knowledge.route.structure;

/**
 * 文档结构解析阶段的节点草稿。
 *
 * <p>草稿只使用文档内稳定序号表达父子关系；持久化前再统一分配数据库主键和兄弟节点指针。</p>
 */
public record DocumentStructureNodeDraft(
        int nodeNo,
        int nodeType,
        Integer parentNodeNo,
        int depth,
        String nodeCode,
        String title,
        String anchorText,
        String canonicalPath,
        String sectionPath,
        String contentText,
        Integer itemIndex) {
}
