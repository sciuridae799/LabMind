package com.labmind.business.chat.knowledge.route.model;

/**
 * 知识路由候选文档。
 *
 * <p>表示一次问题路由命中的文档及其得分拆解，只说明召回依据，不代表已经读取正文证据。</p>
 */
public record KnowledgeRouteCandidate(
        Long documentId,
        String documentName,
        String scopeCode,
        String scopeName,
        String topicCode,
        String topicName,
        double score,
        double semanticScore,
        double lexicalScore,
        double termScore,
        double patternScore,
        java.util.List<String> hitTerms,
        java.util.List<String> matchedPatterns,
        String hitReason) {
}
