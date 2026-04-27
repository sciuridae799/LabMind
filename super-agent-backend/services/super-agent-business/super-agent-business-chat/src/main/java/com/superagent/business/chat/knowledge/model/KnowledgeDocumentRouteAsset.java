package com.superagent.business.chat.knowledge.model;

import java.util.List;

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
