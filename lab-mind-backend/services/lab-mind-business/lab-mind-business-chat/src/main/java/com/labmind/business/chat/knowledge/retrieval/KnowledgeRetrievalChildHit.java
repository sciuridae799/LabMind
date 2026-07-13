package com.labmind.business.chat.knowledge.retrieval;

public record KnowledgeRetrievalChildHit(
        Long chunkId,
        Long documentId,
        Long parentBlockId,
        Integer chunkNo,
        String documentName,
        String sectionPath,
        String chunkText,
        String channel,
        double score,
        int rank) {
}
