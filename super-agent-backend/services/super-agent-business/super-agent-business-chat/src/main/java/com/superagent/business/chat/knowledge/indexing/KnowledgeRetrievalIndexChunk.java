package com.superagent.business.chat.knowledge.indexing;

public record KnowledgeRetrievalIndexChunk(
        Long chunkId,
        Long documentId,
        Long taskId,
        Long planId,
        Long parentBlockId,
        Integer chunkNo,
        Integer sourceType,
        String sectionPath,
        Long structureNodeId,
        Integer structureNodeType,
        String canonicalPath,
        Integer itemIndex,
        String chunkText,
        Integer charCount,
        Integer tokenCount,
        String metadataJson) {
}
