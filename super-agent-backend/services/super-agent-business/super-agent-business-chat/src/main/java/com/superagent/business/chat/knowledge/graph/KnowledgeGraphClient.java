package com.superagent.business.chat.knowledge.graph;

import com.superagent.business.chat.knowledge.model.KnowledgeDocumentRouteAsset;
import com.superagent.business.chat.knowledge.model.KnowledgeRouteCandidate;
import java.util.List;

public interface KnowledgeGraphClient {

    void upsertDocumentRouteAsset(KnowledgeDocumentRouteAsset asset);

    void deleteDocumentRouteAsset(long documentId);

    List<KnowledgeRouteCandidate> routeQuestion(String question, int limit);
}
