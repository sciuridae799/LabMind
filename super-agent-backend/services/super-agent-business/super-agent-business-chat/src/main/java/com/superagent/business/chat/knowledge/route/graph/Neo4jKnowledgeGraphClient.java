package com.superagent.business.chat.knowledge.route.graph;

import com.superagent.business.chat.knowledge.route.config.Neo4jKnowledgeGraphProperties;
import com.superagent.business.chat.knowledge.route.config.KnowledgeRouteProperties;
import com.superagent.business.chat.knowledge.route.model.KnowledgeDocumentRouteAsset;
import com.superagent.business.chat.knowledge.route.model.KnowledgeDocumentStructureGraphNode;
import com.superagent.business.chat.knowledge.route.model.KnowledgeRouteCandidate;
import com.superagent.business.chat.knowledge.route.model.KnowledgeRouteDecision;
import com.superagent.business.chat.knowledge.route.model.KnowledgeRouteRankedCandidate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.Driver;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.SessionConfig;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Neo4j 知识路由图客户端。
 *
 * <p>这是知识库路由召回的图数据库边界。它不保存文档正文，只保存从文档画像中提取出的
 * 知识域、专题、文档、术语和问题模式，用于把用户问题召回到候选文档。</p>
 *
 * <p>写入链路来自文档解析任务：MySQL 生成 document/profile 后调用 upsertDocumentRouteAsset，
 * 将最小路由资产同步到 Neo4j。查询链路来自对话编排器：知识库模式调用 routeQuestion，
 * 根据问题命中的术语和问题模式返回候选文档列表。</p>
 *
 * <p>正文证据不在这里处理。Neo4j 的职责是“找可能相关的文档”，回答边界和提示词由上层执行计划负责。</p>
 */
@Component
@RequiredArgsConstructor
public class Neo4jKnowledgeGraphClient implements KnowledgeGraphClient, DisposableBean {

    // 路由图结构：KnowledgeScope -> KnowledgeTopic -> Document，并把术语和问题模式挂到文档。
    private static final String UPSERT_ROUTE_ASSET_CYPHER = """
            MERGE (scope:KnowledgeScope {code: $scopeCode})
            SET scope.name = $scopeName
            MERGE (topic:KnowledgeTopic {code: $topicCode})
            SET topic.name = $topicName
            MERGE (scope)-[:HAS_TOPIC]->(topic)
            MERGE (document:Document {documentId: $documentId})
            SET document.documentName = $documentName,
                document.status = 'ROUTABLE',
                document.summary = $summary,
                document.routeText = $routeText,
                document.routeTokens = $routeTokens
            MERGE (topic)-[:HAS_DOCUMENT]->(document)
            WITH document
            OPTIONAL MATCH (document)-[oldUses:USES_TERM]->(:Term)
            DELETE oldUses
            WITH document
            OPTIONAL MATCH (:QuestionPattern)-[oldRoutes:ROUTES_TO]->(document)
            DELETE oldRoutes
            WITH document
            UNWIND $terms AS termName
            MERGE (term:Term {normalizedName: termName})
            SET term.name = termName
            MERGE (document)-[:USES_TERM {weight: 1.0}]->(term)
            WITH document
            UNWIND $questionPatterns AS pattern
            MERGE (questionPattern:QuestionPattern {pattern: pattern})
            SET questionPattern.intentType = 'knowledge_question'
            MERGE (questionPattern)-[:ROUTES_TO {weight: 0.8}]->(document)
            """;

    private static final String RANK_SCOPES_CYPHER = """
            MATCH (scope:KnowledgeScope)-[:HAS_TOPIC]->(:KnowledgeTopic)-[:HAS_DOCUMENT]->(document:Document)
            WHERE document.status = 'ROUTABLE'
            OPTIONAL MATCH (document)-[usesTerm:USES_TERM]->(term:Term)
            WITH scope, document,
                 collect(CASE WHEN $question CONTAINS term.normalizedName THEN term.normalizedName ELSE null END) AS rawHitTerms,
                 sum(CASE WHEN $question CONTAINS term.normalizedName THEN usesTerm.weight ELSE 0 END) AS documentTermScore,
                 size([token IN coalesce(document.routeTokens, []) WHERE token IN $queryTokens]) AS documentSemanticHitCount
            OPTIONAL MATCH (questionPattern:QuestionPattern)-[routesTo:ROUTES_TO]->(document)
            WITH scope,
                 collect(rawHitTerms) AS nestedHitTerms,
                 sum(documentTermScore) AS termScore,
                 sum(documentSemanticHitCount) AS semanticScore,
                 collect(CASE WHEN $question CONTAINS questionPattern.pattern THEN questionPattern.pattern ELSE null END) AS rawMatchedPatterns,
                 sum(CASE WHEN $question CONTAINS questionPattern.pattern THEN routesTo.weight ELSE 0 END) AS patternScore
            WITH scope,
                 termScore,
                 semanticScore * $semanticWeight AS semanticScore,
                 coalesce(patternScore, 0) AS patternScore,
                 reduce(hitTerms = [], terms IN nestedHitTerms | hitTerms + terms) AS flattenedHitTerms,
                 rawMatchedPatterns,
                 CASE WHEN $question CONTAINS scope.name THEN 5.0 ELSE 0.0 END AS nameScore
            WITH scope,
                 termScore,
                 patternScore,
                 nameScore,
                 [term IN flattenedHitTerms WHERE term IS NOT NULL] AS hitTerms,
                 [pattern IN rawMatchedPatterns WHERE pattern IS NOT NULL] AS matchedPatterns,
                 semanticScore + ((termScore + patternScore + nameScore) * $lexicalWeight) AS score
            WHERE score > 0
            RETURN scope.code AS scopeCode,
                   scope.name AS scopeName,
                   score AS score,
                   hitTerms AS hitTerms,
                   matchedPatterns AS matchedPatterns
            ORDER BY score DESC, scopeCode DESC
            LIMIT $limit
            """;

    private static final String RANK_TOPICS_CYPHER = """
            MATCH (scope:KnowledgeScope)-[:HAS_TOPIC]->(topic:KnowledgeTopic)-[:HAS_DOCUMENT]->(document:Document)
            WHERE document.status = 'ROUTABLE'
              AND scope.code IN $scopeCodes
            OPTIONAL MATCH (document)-[usesTerm:USES_TERM]->(term:Term)
            WITH scope, topic, document,
                 collect(CASE WHEN $question CONTAINS term.normalizedName THEN term.normalizedName ELSE null END) AS rawHitTerms,
                 sum(CASE WHEN $question CONTAINS term.normalizedName THEN usesTerm.weight ELSE 0 END) AS documentTermScore,
                 size([token IN coalesce(document.routeTokens, []) WHERE token IN $queryTokens]) AS documentSemanticHitCount
            OPTIONAL MATCH (questionPattern:QuestionPattern)-[routesTo:ROUTES_TO]->(document)
            WITH scope, topic,
                 collect(rawHitTerms) AS nestedHitTerms,
                 sum(documentTermScore) AS termScore,
                 sum(documentSemanticHitCount) AS semanticScore,
                 collect(CASE WHEN $question CONTAINS questionPattern.pattern THEN questionPattern.pattern ELSE null END) AS rawMatchedPatterns,
                 sum(CASE WHEN $question CONTAINS questionPattern.pattern THEN routesTo.weight ELSE 0 END) AS patternScore
            WITH scope, topic,
                 termScore,
                 semanticScore * $semanticWeight AS semanticScore,
                 coalesce(patternScore, 0) AS patternScore,
                 reduce(hitTerms = [], terms IN nestedHitTerms | hitTerms + terms) AS flattenedHitTerms,
                 rawMatchedPatterns,
                 CASE WHEN $question CONTAINS topic.name THEN 5.0 ELSE 0.0 END AS nameScore
            WITH scope, topic,
                 termScore,
                 patternScore,
                 nameScore,
                 [term IN flattenedHitTerms WHERE term IS NOT NULL] AS hitTerms,
                 [pattern IN rawMatchedPatterns WHERE pattern IS NOT NULL] AS matchedPatterns,
                 semanticScore + ((termScore + patternScore + nameScore) * $lexicalWeight) AS baseScore
            WHERE baseScore > 0
            RETURN scope.code AS scopeCode,
                   scope.name AS scopeName,
                   topic.code AS topicCode,
                   topic.name AS topicName,
                   baseScore + CASE WHEN scope.code = $topScopeCode THEN $topScopeBoost ELSE 0 END AS score,
                   hitTerms AS hitTerms,
                   matchedPatterns AS matchedPatterns
            ORDER BY score DESC, topicCode DESC
            LIMIT $limit
            """;

    private static final String RANK_DOCUMENTS_CYPHER = """
            MATCH (scope:KnowledgeScope)-[:HAS_TOPIC]->(topic:KnowledgeTopic)-[:HAS_DOCUMENT]->(document:Document)
            WHERE document.status = 'ROUTABLE'
              AND scope.code IN $scopeCodes
              AND ($topicFilterEnabled = false OR topic.code IN $topicCodes)
            OPTIONAL MATCH (document)-[usesTerm:USES_TERM]->(term:Term)
            WITH scope, topic, document,
                 collect(CASE WHEN $question CONTAINS term.normalizedName THEN term.normalizedName ELSE null END) AS rawHitTerms,
                 sum(CASE WHEN $question CONTAINS term.normalizedName THEN usesTerm.weight ELSE 0 END) AS termScore,
                 size([token IN coalesce(document.routeTokens, []) WHERE token IN $queryTokens]) * $semanticWeight AS semanticScore
            OPTIONAL MATCH (questionPattern:QuestionPattern)-[routesTo:ROUTES_TO]->(document)
            WITH scope, topic, document, rawHitTerms, termScore,
                 semanticScore,
                 collect(CASE WHEN $question CONTAINS questionPattern.pattern THEN questionPattern.pattern ELSE null END) AS rawMatchedPatterns,
                 sum(CASE WHEN $question CONTAINS questionPattern.pattern THEN routesTo.weight ELSE 0 END) AS patternScore,
                 CASE
                   WHEN document.summary IS NOT NULL AND $question CONTAINS document.summary THEN 2.0
                   WHEN document.summary IS NOT NULL AND document.summary CONTAINS $question THEN 2.0
                   ELSE 0.0
                 END AS summaryScore
            WITH scope, topic, document,
                 termScore,
                 semanticScore,
                 coalesce(patternScore, 0) AS patternScore,
                 summaryScore,
                 [term IN rawHitTerms WHERE term IS NOT NULL] AS hitTerms,
                 [pattern IN rawMatchedPatterns WHERE pattern IS NOT NULL] AS matchedPatterns,
                 ((termScore + coalesce(patternScore, 0) + summaryScore) * $lexicalWeight) AS lexicalScore,
                 semanticScore + ((termScore + coalesce(patternScore, 0) + summaryScore) * $lexicalWeight) AS baseScore
            WHERE baseScore > 0
            RETURN document.documentId AS documentId,
                   document.documentName AS documentName,
                   scope.code AS scopeCode,
                   scope.name AS scopeName,
                   topic.code AS topicCode,
                   topic.name AS topicName,
                   termScore AS termScore,
                   patternScore AS patternScore,
                   semanticScore AS semanticScore,
                   lexicalScore AS lexicalScore,
                   baseScore
                     + CASE WHEN scope.code = $topScopeCode THEN $topScopeDocumentBoost ELSE 0 END
                     + CASE WHEN topic.code = $topTopicCode THEN $topTopicDocumentBoost ELSE 0 END AS score,
                   hitTerms AS hitTerms,
                   matchedPatterns AS matchedPatterns,
                   '知识域收缩：' + scope.name + ' / ' + topic.name
                     + '；术语命中：' + reduce(text = '', term IN hitTerms | text + CASE WHEN text = '' THEN term ELSE ',' + term END)
                     + '；问题模式命中：' + reduce(text = '', pattern IN matchedPatterns | text + CASE WHEN text = '' THEN pattern ELSE ',' + pattern END) AS hitReason
            ORDER BY score DESC, documentId DESC
            LIMIT $limit
            """;

    private static final String DELETE_DOCUMENT_STRUCTURE_CYPHER = """
            MATCH (document:Document {documentId: $documentId})
            OPTIONAL MATCH (document)-[:HAS_CHILD*1..]->(node)
            WITH collect(DISTINCT node) AS nodes
            FOREACH (node IN nodes | DETACH DELETE node)
            """;

    private static final String SET_DOCUMENT_ROOT_CYPHER = """
            MERGE (document:Document {documentId: $documentId})
            SET document.documentName = $documentName,
                document.nodeType = 'DOCUMENT',
                document.canonicalPath = $documentName,
                document.title = $documentName
            """;

    private static final String UPSERT_SECTION_NODE_CYPHER = """
            MERGE (node:Section {nodeId: $nodeId})
            SET node.documentId = $documentId,
                node.nodeNo = $nodeNo,
                node.nodeType = 'SECTION',
                node.depth = $depth,
                node.nodeCode = $nodeCode,
                node.title = $title,
                node.anchorText = $anchorText,
                node.canonicalPath = $canonicalPath,
                node.sectionPath = $sectionPath,
                node.contentText = $contentText,
                node.itemIndex = $itemIndex
            """;

    private static final String UPSERT_ITEM_NODE_CYPHER = """
            MERGE (node:Item {nodeId: $nodeId})
            SET node.documentId = $documentId,
                node.nodeNo = $nodeNo,
                node.nodeType = 'ITEM',
                node.depth = $depth,
                node.nodeCode = $nodeCode,
                node.title = $title,
                node.anchorText = $anchorText,
                node.canonicalPath = $canonicalPath,
                node.sectionPath = $sectionPath,
                node.contentText = $contentText,
                node.itemIndex = $itemIndex
            """;

    private static final String LINK_DOCUMENT_CHILD_CYPHER = """
            MATCH (document:Document {documentId: $documentId})
            MATCH (child {nodeId: $childNodeId})
            MERGE (document)-[:HAS_CHILD]->(child)
            """;

    private static final String LINK_NODE_CHILD_CYPHER = """
            MATCH (parent {nodeId: $parentNodeId})
            MATCH (child {nodeId: $childNodeId})
            MERGE (parent)-[:HAS_CHILD]->(child)
            """;

    private static final String LINK_NEXT_SIBLING_CYPHER = """
            MATCH (left {nodeId: $leftNodeId})
            MATCH (right {nodeId: $rightNodeId})
            MERGE (left)-[:NEXT_SIBLING]->(right)
            """;

    private static final String DELETE_ROUTE_ASSET_CYPHER = """
            MATCH (document:Document {documentId: $documentId})
            DETACH DELETE document
            """;

    private final Neo4jKnowledgeGraphProperties properties;

    private final KnowledgeRouteProperties routeProperties;

    private volatile Driver driver;

    /**
     * 写入或更新单个文档的路由资产。
     *
     * <p>资产来源于已通过校验的文档画像。这里使用 MERGE 保持同一 documentId 的节点唯一，
     * 并把术语、问题模式重新挂到文档节点上。</p>
     */
    @Override
    public void upsertDocumentRouteAsset(KnowledgeDocumentRouteAsset asset) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("documentId", asset.documentId());
        parameters.put("documentName", asset.documentName());
        parameters.put("scopeCode", asset.scopeCode());
        parameters.put("scopeName", asset.scopeName());
        parameters.put("topicCode", asset.topicCode());
        parameters.put("topicName", asset.topicName());
        parameters.put("summary", asset.summary());
        parameters.put("terms", asset.terms());
        parameters.put("questionPatterns", asset.questionPatterns());
        String routeText = buildRouteText(asset);
        parameters.put("routeText", routeText);
        parameters.put("routeTokens", tokenize(routeText));
        try (var session = getRequiredDriver().session(sessionConfig())) {
            session.executeWriteWithoutResult(tx -> tx.run(UPSERT_ROUTE_ASSET_CYPHER, parameters).consume());
        }
    }

    @Override
    public void replaceDocumentStructure(
            long documentId,
            String documentName,
            List<KnowledgeDocumentStructureGraphNode> nodes) {
        // 结构图必须以一次解析任务产物为准整体替换，避免旧章节在 Neo4j 中残留并被结构查询命中。
        try (var session = getRequiredDriver().session(sessionConfig())) {
            session.executeWriteWithoutResult(tx -> {
                tx.run(DELETE_DOCUMENT_STRUCTURE_CYPHER, Map.of("documentId", documentId)).consume();
                tx.run(SET_DOCUMENT_ROOT_CYPHER, Map.of(
                        "documentId", documentId,
                        "documentName", documentName)).consume();
                Map<Long, KnowledgeDocumentStructureGraphNode> nodeMap = new HashMap<>();
                for (KnowledgeDocumentStructureGraphNode node : nodes) {
                    if (Integer.valueOf(1).equals(node.nodeType())) {
                        continue;
                    }
                    nodeMap.put(node.nodeId(), node);
                    tx.run(Integer.valueOf(2).equals(node.nodeType())
                                    ? UPSERT_SECTION_NODE_CYPHER
                                    : UPSERT_ITEM_NODE_CYPHER,
                            nodeParameters(documentId, node)).consume();
                }
                for (KnowledgeDocumentStructureGraphNode node : nodeMap.values()) {
                    if (node.parentNodeId() == null || !nodeMap.containsKey(node.parentNodeId())) {
                        tx.run(LINK_DOCUMENT_CHILD_CYPHER, Map.of(
                                "documentId", documentId,
                                "childNodeId", node.nodeId())).consume();
                    } else {
                        tx.run(LINK_NODE_CHILD_CYPHER, Map.of(
                                "parentNodeId", node.parentNodeId(),
                                "childNodeId", node.nodeId())).consume();
                    }
                    if (node.nextSiblingNodeId() != null && nodeMap.containsKey(node.nextSiblingNodeId())) {
                        tx.run(LINK_NEXT_SIBLING_CYPHER, Map.of(
                                "leftNodeId", node.nodeId(),
                                "rightNodeId", node.nextSiblingNodeId())).consume();
                    }
                }
            });
        }
    }

    /**
     * 根据用户问题召回知识候选文档。
     *
     * <p>路由按 Scope -> Topic -> Document 三层递进收缩：先确定候选知识域，再在候选知识域内
     * 收缩专题，最后只在收缩后的范围内选文档。返回结果只表达“路由相关性”，不表达最终回答质量。</p>
     */
    @Override
    public KnowledgeRouteDecision routeQuestion(String question, int limit) {
        try (var session = getRequiredDriver().session(sessionConfig())) {
            return session.executeRead(tx -> {
                List<RankedScope> rankedScopes = tx.run(RANK_SCOPES_CYPHER, Map.of(
                                "question", question,
                                "queryTokens", tokenize(question),
                                "semanticWeight", routeProperties.getSemanticWeight(),
                                "lexicalWeight", routeProperties.getLexicalWeight(),
                                "limit", routeProperties.getScopeTopK()))
                        .list(record -> new RankedScope(
                                record.get("scopeCode").asString(),
                                record.get("scopeName").asString(),
                                record.get("score").asDouble(),
                                buildRankHitReason(
                                        record.get("hitTerms").asList(value -> value.asString()),
                                        record.get("matchedPatterns").asList(value -> value.asString()))));
                if (rankedScopes.isEmpty()) {
                    return KnowledgeRouteDecision.empty();
                }
                List<String> scopeCodes = rankedScopes.stream().map(RankedScope::scopeCode).toList();
                String topScopeCode = rankedScopes.get(0).scopeCode();
                List<RankedTopic> rankedTopics = tx.run(RANK_TOPICS_CYPHER, Map.of(
                                "question", question,
                                "queryTokens", tokenize(question),
                                "semanticWeight", routeProperties.getSemanticWeight(),
                                "lexicalWeight", routeProperties.getLexicalWeight(),
                                "scopeCodes", scopeCodes,
                                "topScopeCode", topScopeCode,
                                "topScopeBoost", routeProperties.getTopScopeTopicBoost(),
                                "limit", routeProperties.getTopicTopK()))
                        .list(record -> new RankedTopic(
                                record.get("scopeCode").asString(),
                                record.get("scopeName").asString(),
                                record.get("topicCode").asString(),
                                record.get("topicName").asString(),
                                record.get("score").asDouble(),
                                buildRankHitReason(
                                        record.get("hitTerms").asList(value -> value.asString()),
                                        record.get("matchedPatterns").asList(value -> value.asString()))));
                List<String> topicCodes = rankedTopics.stream().map(RankedTopic::topicCode).toList();
                String topTopicCode = rankedTopics.isEmpty() ? "" : rankedTopics.get(0).topicCode();
                Map<String, Object> documentRankParameters = new HashMap<>();
                documentRankParameters.put("question", question);
                documentRankParameters.put("queryTokens", tokenize(question));
                documentRankParameters.put("semanticWeight", routeProperties.getSemanticWeight());
                documentRankParameters.put("lexicalWeight", routeProperties.getLexicalWeight());
                documentRankParameters.put("scopeCodes", scopeCodes);
                documentRankParameters.put("topicCodes", topicCodes);
                documentRankParameters.put("topicFilterEnabled", !topicCodes.isEmpty());
                documentRankParameters.put("topScopeCode", topScopeCode);
                documentRankParameters.put("topTopicCode", topTopicCode);
                documentRankParameters.put("topScopeDocumentBoost", routeProperties.getTopScopeDocumentBoost());
                documentRankParameters.put("topTopicDocumentBoost", routeProperties.getTopTopicDocumentBoost());
                documentRankParameters.put("limit", limit);
                List<KnowledgeRouteCandidate> documentCandidates = tx.run(RANK_DOCUMENTS_CYPHER, documentRankParameters)
                        .list(record -> new KnowledgeRouteCandidate(
                                record.get("documentId").asLong(),
                                record.get("documentName").asString(),
                                record.get("scopeCode").asString(),
                                record.get("scopeName").asString(),
                                record.get("topicCode").asString(),
                                record.get("topicName").asString(),
                                record.get("score").asDouble(),
                                record.get("semanticScore").asDouble(),
                                record.get("lexicalScore").asDouble(),
                                record.get("termScore").asDouble(),
                                record.get("patternScore").asDouble(),
                                record.get("hitTerms").asList(value -> value.asString()),
                                record.get("matchedPatterns").asList(value -> value.asString()),
                                record.get("hitReason").asString()));
                return new KnowledgeRouteDecision(
                        rankedScopes.stream()
                                .map(scope -> new KnowledgeRouteRankedCandidate(
                                        "SCOPE",
                                        scope.scopeCode(),
                                        scope.scopeName(),
                                        scope.score(),
                                        scope.hitReason()))
                                .toList(),
                        rankedTopics.stream()
                                .map(topic -> new KnowledgeRouteRankedCandidate(
                                        "TOPIC",
                                        topic.topicCode(),
                                        topic.topicName(),
                                        topic.score(),
                                        topic.hitReason()))
                                .toList(),
                        documentCandidates);
            });
        }
    }

    private record RankedScope(String scopeCode, String scopeName, double score, String hitReason) {
    }

    private record RankedTopic(
            String scopeCode,
            String scopeName,
            String topicCode,
            String topicName,
            double score,
            String hitReason) {
    }

    private String buildRankHitReason(List<String> hitTerms, List<String> matchedPatterns) {
        return "术语命中：" + String.join(",", hitTerms)
                + "；问题模式命中：" + String.join(",", matchedPatterns);
    }

    private Map<String, Object> nodeParameters(long documentId, KnowledgeDocumentStructureGraphNode node) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("documentId", documentId);
        parameters.put("nodeId", node.nodeId());
        parameters.put("nodeNo", node.nodeNo());
        parameters.put("depth", node.depth());
        parameters.put("nodeCode", node.nodeCode());
        parameters.put("title", node.title());
        parameters.put("anchorText", node.anchorText());
        parameters.put("canonicalPath", node.canonicalPath());
        parameters.put("sectionPath", node.sectionPath());
        parameters.put("contentText", node.contentText());
        parameters.put("itemIndex", node.itemIndex());
        return parameters;
    }

    /**
     * 构建文档级路由文本。
     *
     * <p>语义打分只使用画像、术语和问题模式，不读取正文，避免路由阶段和证据阶段混在一起。</p>
     */
    private String buildRouteText(KnowledgeDocumentRouteAsset asset) {
        List<String> parts = new ArrayList<>();
        parts.add(asset.documentName());
        parts.add(asset.scopeName());
        parts.add(asset.topicName());
        parts.add(asset.summary());
        parts.addAll(asset.terms());
        parts.addAll(asset.questionPatterns());
        return String.join(" ", parts.stream().filter(StringUtils::hasText).toList());
    }

    /**
     * 将中文和英文路由文本切成可比较的轻量 token。
     *
     * <p>当前实现是确定性语义近似信号：英文按词，中文按二元片段，用于和词法命中融合打分。</p>
     */
    private List<String> tokenize(String text) {
        if (!StringUtils.hasText(text)) {
            return List.of();
        }
        String normalized = text.toLowerCase(Locale.ROOT).replaceAll("[^\\p{IsHan}a-z0-9]+", " ").strip();
        List<String> tokens = new ArrayList<>();
        Arrays.stream(normalized.split("\\s+"))
                .filter(StringUtils::hasText)
                .filter(token -> token.length() > 1)
                .forEach(tokens::add);
        String hanOnly = normalized.replaceAll("[^\\p{IsHan}]", "");
        for (int index = 0; index + 1 < hanOnly.length(); index++) {
            tokens.add(hanOnly.substring(index, index + 2));
        }
        return tokens.stream().distinct().limit(200).toList();
    }

    /**
     * 删除文档路由资产。
     *
     * <p>文档被删除时必须同步移除图节点，否则前台列表不可见的文档仍可能被知识路由召回。</p>
     */
    @Override
    public void deleteDocumentRouteAsset(long documentId) {
        try (var session = getRequiredDriver().session(sessionConfig())) {
            session.executeWriteWithoutResult(tx -> tx.run(
                    DELETE_ROUTE_ASSET_CYPHER,
                    Map.of("documentId", documentId)).consume());
        }
    }

    private Driver getRequiredDriver() {
        Driver currentDriver = driver;
        if (currentDriver != null) {
            return currentDriver;
        }
        synchronized (this) {
            if (driver == null) {
                // 首次使用时才校验并创建 Driver，避免未启用知识路由的启动流程被 Neo4j 配置阻断。
                validateConfiguredText(properties.getUri(), "SUPER_AGENT_NEO4J_URI");
                validateConfiguredText(properties.getUsername(), "SUPER_AGENT_NEO4J_USERNAME");
                validateConfiguredText(properties.getPassword(), "SUPER_AGENT_NEO4J_PASSWORD");
                validateConfiguredText(properties.getDatabase(), "SUPER_AGENT_NEO4J_DATABASE");
                driver = GraphDatabase.driver(
                        properties.getUri().trim(),
                        AuthTokens.basic(properties.getUsername().trim(), properties.getPassword()));
            }
            return driver;
        }
    }

    private SessionConfig sessionConfig() {
        validateConfiguredText(properties.getDatabase(), "SUPER_AGENT_NEO4J_DATABASE");
        return SessionConfig.forDatabase(properties.getDatabase().trim());
    }

    private void validateConfiguredText(String value, String envName) {
        if (!StringUtils.hasText(value) || value.contains("${")) {
            throw new IllegalStateException(envName + " must be configured before using knowledge graph routing.");
        }
    }

    @Override
    public void destroy() {
        Driver currentDriver = driver;
        if (currentDriver != null) {
            currentDriver.close();
        }
    }
}
