package com.superagent.business.chat.knowledge.graph;

import com.superagent.business.chat.knowledge.config.Neo4jKnowledgeGraphProperties;
import com.superagent.business.chat.knowledge.model.KnowledgeDocumentRouteAsset;
import com.superagent.business.chat.knowledge.model.KnowledgeRouteCandidate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
                document.summary = $summary
            MERGE (topic)-[:HAS_DOCUMENT]->(document)
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

    // 候选召回只依赖术语和问题模式命中分，不读取正文内容，正文约束由执行计划提示词承担。
    private static final String ROUTE_QUESTION_CYPHER = """
            MATCH (document:Document)-[usesTerm:USES_TERM]->(term:Term)
            WHERE document.status = 'ROUTABLE'
              AND $question CONTAINS term.normalizedName
            MATCH (topic:KnowledgeTopic)-[:HAS_DOCUMENT]->(document)
            MATCH (scope:KnowledgeScope)-[:HAS_TOPIC]->(topic)
            WITH document, topic, scope, collect(term.normalizedName) AS hitTerms, sum(usesTerm.weight) AS termScore
            OPTIONAL MATCH (questionPattern:QuestionPattern)-[routesTo:ROUTES_TO]->(document)
            WITH document, topic, scope, hitTerms, termScore,
                 collect(CASE WHEN $question CONTAINS questionPattern.pattern THEN questionPattern.pattern ELSE null END) AS rawMatchedPatterns,
                 sum(CASE WHEN $question CONTAINS questionPattern.pattern THEN routesTo.weight ELSE 0 END) AS patternScore
            RETURN document.documentId AS documentId,
                   document.documentName AS documentName,
                   scope.code AS scopeCode,
                   scope.name AS scopeName,
                   topic.code AS topicCode,
                   topic.name AS topicName,
                   termScore AS termScore,
                   coalesce(patternScore, 0) AS patternScore,
                   termScore + coalesce(patternScore, 0) AS score,
                   hitTerms AS hitTerms,
                   [pattern IN rawMatchedPatterns WHERE pattern IS NOT NULL] AS matchedPatterns,
                   '术语命中：' + reduce(text = '', term IN hitTerms | text + CASE WHEN text = '' THEN term ELSE ',' + term END) AS hitReason
            ORDER BY score DESC, documentId DESC
            LIMIT $limit
            """;

    private static final String DELETE_ROUTE_ASSET_CYPHER = """
            MATCH (document:Document {documentId: $documentId})
            DETACH DELETE document
            """;

    private final Neo4jKnowledgeGraphProperties properties;

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
        try (var session = getRequiredDriver().session(sessionConfig())) {
            session.executeWriteWithoutResult(tx -> tx.run(UPSERT_ROUTE_ASSET_CYPHER, parameters).consume());
        }
    }

    /**
     * 根据用户问题召回知识候选文档。
     *
     * <p>召回分数由术语命中和问题模式命中组成，只表达“路由相关性”，不表达最终回答质量。
     * 返回结果会进入执行计划和 debugTrace。</p>
     */
    @Override
    public List<KnowledgeRouteCandidate> routeQuestion(String question, int limit) {
        try (var session = getRequiredDriver().session(sessionConfig())) {
            return session.executeRead(tx -> tx.run(ROUTE_QUESTION_CYPHER, Map.of(
                            "question", question,
                            "limit", limit))
                    .list(record -> new KnowledgeRouteCandidate(
                            record.get("documentId").asLong(),
                            record.get("documentName").asString(),
                            record.get("scopeCode").asString(),
                            record.get("scopeName").asString(),
                            record.get("topicCode").asString(),
                            record.get("topicName").asString(),
                            record.get("score").asDouble(),
                            record.get("termScore").asDouble(),
                            record.get("patternScore").asDouble(),
                            record.get("hitTerms").asList(value -> value.asString()),
                            record.get("matchedPatterns").asList(value -> value.asString()),
                            record.get("hitReason").asString())));
        }
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
