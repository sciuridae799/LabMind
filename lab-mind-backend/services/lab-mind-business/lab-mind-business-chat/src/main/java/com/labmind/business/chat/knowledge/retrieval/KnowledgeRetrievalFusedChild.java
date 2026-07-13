package com.labmind.business.chat.knowledge.retrieval;

import java.util.List;

public record KnowledgeRetrievalFusedChild(
        Long chunkId,
        Long documentId,
        Long parentBlockId,
        Integer chunkNo,
        String documentName,
        String sectionPath,
        String chunkText,
        double rrfScore,
        Double rerankScore,
        List<String> channels) {

    public double finalScore() {
        return rerankScore == null ? rrfScore : rerankScore;
    }
}
