package com.superagent.business.chat.knowledge.route.model;

/**
 * 知识路由单层候选。
 */
public record KnowledgeRouteRankedCandidate(
        String candidateType,
        String candidateId,
        String candidateName,
        double score,
        String hitReason) {
}
