package com.superagent.business.chat.knowledge.retrieval;

import java.util.List;

public record KnowledgeRetrievalResult(
        String evidenceContextText,
        List<KnowledgeRetrievalParentEvidence> parentEvidenceList) {

    public static KnowledgeRetrievalResult empty() {
        return new KnowledgeRetrievalResult(null, List.of());
    }
}
