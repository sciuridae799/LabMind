package com.superagent.business.chat.knowledge.model;

public record KnowledgeRouteCandidate(
        Long documentId,
        String documentName,
        String scopeCode,
        String scopeName,
        String topicCode,
        String topicName,
        double score,
        String hitReason) {
}
