package com.labmind.business.chat.knowledge.route.graph;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.labmind.business.chat.knowledge.route.config.KnowledgeRouteProperties;
import com.labmind.business.chat.knowledge.route.config.Neo4jKnowledgeGraphProperties;
import com.labmind.business.chat.knowledge.route.model.KnowledgeDocumentRouteAsset;
import com.labmind.business.chat.knowledge.route.model.KnowledgeDocumentStructureGraphNode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Record;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.SessionConfig;
import org.neo4j.driver.TransactionCallback;
import org.neo4j.driver.TransactionContext;
import org.neo4j.driver.Values;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class Neo4jKnowledgeGraphClientTest {

    @Mock
    private Driver driver;

    @Mock
    private Session session;

    @Mock
    private TransactionContext transaction;

    private Neo4jKnowledgeGraphClient client;

    private List<ExecutedQuery> executedQueries;

    @BeforeEach
    void setUp() {
        Neo4jKnowledgeGraphProperties graphProperties = new Neo4jKnowledgeGraphProperties();
        graphProperties.setDatabase("neo4j");
        client = new Neo4jKnowledgeGraphClient(graphProperties, new KnowledgeRouteProperties());
        ReflectionTestUtils.setField(client, "driver", driver);
        executedQueries = new ArrayList<>();

        when(driver.session(any(SessionConfig.class))).thenReturn(session);
        when(transaction.run(anyString(), org.mockito.ArgumentMatchers.<Map<String, Object>>any()))
                .thenAnswer(invocation -> {
                    String query = invocation.getArgument(0);
                    Map<String, Object> parameters = invocation.getArgument(1);
                    executedQueries.add(new ExecutedQuery(query, new HashMap<>(parameters)));
                    return resultFor(query);
                });
    }

    @Test
    void shouldFilterEveryRankingStageByWorkspaceBeforeLimit() {
        doAnswer(invocation -> {
            TransactionCallback<Object> callback = invocation.getArgument(0);
            return callback.execute(transaction);
        }).when(session).executeRead(org.mockito.ArgumentMatchers.<TransactionCallback<Object>>any());

        var decision = client.routeQuestion("workspace-1", "订单审核怎么配置", 5);

        assertThat(decision.documentCandidates()).extracting(candidate -> candidate.documentId())
                .containsExactly(9001L);
        List<ExecutedQuery> rankingQueries = executedQueries.stream()
                .filter(query -> query.query().contains("LIMIT $limit"))
                .toList();
        assertThat(rankingQueries).hasSize(3).allSatisfy(query -> {
            assertThat(query.parameters()).containsEntry("workspaceId", "workspace-1");
            assertThat(query.query()).contains("document.workspaceId = $workspaceId");
            assertThat(query.query().indexOf("document.workspaceId = $workspaceId"))
                    .isLessThan(query.query().indexOf("LIMIT $limit"));
        });
    }

    @Test
    void shouldWriteWorkspaceOnRouteAndStructureAssets() {
        prepareWriteTransaction();
        client.upsertDocumentRouteAsset(new KnowledgeDocumentRouteAsset(
                9001L,
                "workspace-1",
                "订单审核手册",
                "order_scope",
                "订单知识域",
                "audit_topic",
                "审核专题",
                "订单审核流程",
                List.of("订单审核"),
                List.of("订单审核怎么配置")));

        ExecutedQuery routeAssetQuery = executedQueries.get(0);
        assertThat(routeAssetQuery.parameters()).containsEntry("workspaceId", "workspace-1");
        assertThat(routeAssetQuery.query())
                .contains("SET document.workspaceId = $workspaceId")
                .contains("WHERE oldTopic <> topic")
                .contains("DELETE oldContains");

        executedQueries.clear();
        client.replaceDocumentStructure(
                "workspace-1",
                9001L,
                "订单审核手册",
                List.of(
                        structureNode(101L, 2, null, 102L),
                        structureNode(102L, 3, 101L, null)));

        assertThat(executedQueries).isNotEmpty().allSatisfy(query ->
                assertThat(query.parameters()).containsEntry("workspaceId", "workspace-1"));
        assertThat(executedQueries).anySatisfy(query -> assertThat(query.query())
                .contains("MATCH (document:Document {documentId: $documentId, workspaceId: $workspaceId})"));
        assertThat(executedQueries).anySatisfy(query -> assertThat(query.query())
                .contains("SET node.workspaceId = $workspaceId"));
        assertThat(executedQueries).anySatisfy(query -> assertThat(query.query())
                .contains("MATCH (parent {nodeId: $parentNodeId, workspaceId: $workspaceId})"));
    }

    @Test
    void shouldDeleteOnlyWorkspaceDocumentAndItsStructure() {
        prepareWriteTransaction();

        client.deleteDocumentRouteAsset("workspace-1", 9001L);

        assertThat(executedQueries).singleElement().satisfies(query -> {
            assertThat(query.parameters())
                    .containsEntry("workspaceId", "workspace-1")
                    .containsEntry("documentId", 9001L);
            assertThat(query.query())
                    .contains("MATCH (document:Document {documentId: $documentId, workspaceId: $workspaceId})")
                    .contains("OPTIONAL MATCH (document)-[:HAS_CHILD*1..]->(node)")
                    .contains("FOREACH (node IN nodes | DETACH DELETE node)")
                    .contains("DETACH DELETE document");
        });
    }

    private void prepareWriteTransaction() {
        doAnswer(invocation -> {
            Consumer<TransactionContext> consumer = invocation.getArgument(0);
            consumer.accept(transaction);
            return null;
        }).when(session).executeWriteWithoutResult(
                org.mockito.ArgumentMatchers.<Consumer<TransactionContext>>any());
    }

    private Result resultFor(String query) {
        if (query.contains("RETURN document.documentId AS documentId")) {
            return result(List.of(record(Map.ofEntries(
                    Map.entry("documentId", 9001L),
                    Map.entry("documentName", "订单审核手册"),
                    Map.entry("scopeCode", "order_scope"),
                    Map.entry("scopeName", "订单知识域"),
                    Map.entry("topicCode", "audit_topic"),
                    Map.entry("topicName", "审核专题"),
                    Map.entry("termScore", 1.0D),
                    Map.entry("patternScore", 0.8D),
                    Map.entry("semanticScore", 1.6D),
                    Map.entry("lexicalScore", 1.8D),
                    Map.entry("score", 3.4D),
                    Map.entry("hitTerms", List.of("订单审核")),
                    Map.entry("matchedPatterns", List.of("订单审核怎么配置")),
                    Map.entry("hitReason", "术语命中：订单审核")))));
        }
        if (query.contains("topic.code AS topicCode")) {
            return result(List.of(record(Map.of(
                    "scopeCode", "order_scope",
                    "scopeName", "订单知识域",
                    "topicCode", "audit_topic",
                    "topicName", "审核专题",
                    "score", 3.4D,
                    "hitTerms", List.of("订单审核"),
                    "matchedPatterns", List.of("订单审核怎么配置")))));
        }
        if (query.contains("RETURN scope.code AS scopeCode")) {
            return result(List.of(record(Map.of(
                    "scopeCode", "order_scope",
                    "scopeName", "订单知识域",
                    "score", 3.4D,
                    "hitTerms", List.of("订单审核"),
                    "matchedPatterns", List.of("订单审核怎么配置")))));
        }
        Result result = mock(Result.class);
        when(result.consume()).thenReturn(mock(org.neo4j.driver.summary.ResultSummary.class));
        return result;
    }

    private Result result(List<Record> records) {
        Result result = mock(Result.class);
        doAnswer(invocation -> {
            Function<Record, Object> mapper = invocation.getArgument(0);
            return records.stream().map(mapper).toList();
        }).when(result).list(org.mockito.ArgumentMatchers.<Function<Record, Object>>any());
        return result;
    }

    private Record record(Map<String, Object> values) {
        Record record = mock(Record.class);
        when(record.get(anyString())).thenAnswer(invocation ->
                Values.value(values.get(invocation.<String>getArgument(0))));
        return record;
    }

    private KnowledgeDocumentStructureGraphNode structureNode(
            long nodeId,
            int nodeType,
            Long parentNodeId,
            Long nextSiblingNodeId) {
        return new KnowledgeDocumentStructureGraphNode(
                nodeId,
                Math.toIntExact(nodeId),
                nodeType,
                parentNodeId,
                null,
                nextSiblingNodeId,
                nodeType - 1,
                "node-" + nodeId,
                "节点 " + nodeId,
                "节点 " + nodeId,
                "订单审核手册/节点 " + nodeId,
                "节点 " + nodeId,
                "节点内容 " + nodeId,
                nodeType == 3 ? 1 : null);
    }

    private record ExecutedQuery(String query, Map<String, Object> parameters) {
    }
}
