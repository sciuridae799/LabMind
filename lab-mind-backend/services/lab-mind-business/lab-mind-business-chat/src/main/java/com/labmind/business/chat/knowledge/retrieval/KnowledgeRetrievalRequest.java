package com.labmind.business.chat.knowledge.retrieval;

import java.util.List;

public record KnowledgeRetrievalRequest(
        String question,
        List<Long> documentIdList) {
}
