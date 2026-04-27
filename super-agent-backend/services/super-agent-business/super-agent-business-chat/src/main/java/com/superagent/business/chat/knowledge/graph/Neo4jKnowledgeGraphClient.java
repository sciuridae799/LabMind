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

@Component
@RequiredArgsConstructor
public class Neo4jKnowledgeGraphClient implements KnowledgeGraphClient, DisposableBean {

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

    private static final String ROUTE_QUESTION_CYPHER = """
            MATCH (document:Document)-[usesTerm:USES_TERM]->(term:Term)
            WHERE document.status = 'ROUTABLE'
              AND $question CONTAINS term.normalizedName
            MATCH (topic:KnowledgeTopic)-[:HAS_DOCUMENT]->(document)
            MATCH (scope:KnowledgeScope)-[:HAS_TOPIC]->(topic)
            WITH document, topic, scope, collect(term.normalizedName) AS hitTerms, sum(usesTerm.weight) AS termScore
            OPTIONAL MATCH (questionPattern:QuestionPattern)-[routesTo:ROUTES_TO]->(document)
            WITH document, topic, scope, hitTerms, termScore,
                 sum(CASE WHEN $question CONTAINS questionPattern.pattern THEN routesTo.weight ELSE 0 END) AS patternScore
            RETURN document.documentId AS documentId,
                   document.documentName AS documentName,
                   scope.code AS scopeCode,
                   scope.name AS scopeName,
                   topic.code AS topicCode,
                   topic.name AS topicName,
                   termScore + patternScore AS score,
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
                            record.get("hitReason").asString())));
        }
    }

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
