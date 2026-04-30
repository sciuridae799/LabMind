package com.superagent.business.chat.knowledge.route.graph;

import com.superagent.business.chat.knowledge.route.model.KnowledgeDocumentRouteAsset;
import com.superagent.business.chat.knowledge.route.model.KnowledgeDocumentStructureGraphNode;
import com.superagent.business.chat.knowledge.route.model.KnowledgeRouteDecision;
import java.util.List;

/**
 * 知识路由图抽象接口。
 *
 * <p>业务层只依赖可路由资产写入、问题召回候选和资产删除三种能力，不感知底层图数据库实现。</p>
 */
public interface KnowledgeGraphClient {

    void upsertDocumentRouteAsset(KnowledgeDocumentRouteAsset asset);

    void replaceDocumentStructure(long documentId, String documentName, List<KnowledgeDocumentStructureGraphNode> nodes);

    void deleteDocumentRouteAsset(long documentId);

    KnowledgeRouteDecision routeQuestion(String question, int limit);
}
