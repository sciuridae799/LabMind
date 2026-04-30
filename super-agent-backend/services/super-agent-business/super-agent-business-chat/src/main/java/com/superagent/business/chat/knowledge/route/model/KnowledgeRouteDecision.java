package com.superagent.business.chat.knowledge.route.model;

import java.util.List;

/**
 * 一次知识路由的完整三级决策结果。
 */
public record KnowledgeRouteDecision(
        List<KnowledgeRouteRankedCandidate> scopeCandidates,
        List<KnowledgeRouteRankedCandidate> topicCandidates,
        List<KnowledgeRouteCandidate> documentCandidates) {

    public static KnowledgeRouteDecision empty() {
        return new KnowledgeRouteDecision(List.of(), List.of(), List.of());
    }
}
