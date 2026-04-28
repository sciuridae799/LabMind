package com.superagent.business.chat.knowledge.graph;

import com.superagent.business.chat.knowledge.model.KnowledgeDocumentRouteAsset;
import com.superagent.business.chat.knowledge.model.KnowledgeRouteCandidate;
import java.util.List;

/**
 * 知识路由图抽象接口。
 *
 * <p>业务层只依赖可路由资产写入、问题召回候选和资产删除三种能力，不感知底层图数据库实现。</p>
 */
public interface KnowledgeGraphClient {

    void upsertDocumentRouteAsset(KnowledgeDocumentRouteAsset asset);

    void deleteDocumentRouteAsset(long documentId);

    List<KnowledgeRouteCandidate> routeQuestion(String question, int limit);
}
