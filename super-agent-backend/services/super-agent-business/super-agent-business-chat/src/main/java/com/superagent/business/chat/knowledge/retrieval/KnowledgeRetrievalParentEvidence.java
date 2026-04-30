package com.superagent.business.chat.knowledge.retrieval;

import java.util.List;

public record KnowledgeRetrievalParentEvidence(
        Long parentBlockId,
        Long documentId,
        String documentName,
        String sectionPath,
        String parentText,
        double score,
        List<Long> hitChunkIdList,
        List<String> channels) {
}
