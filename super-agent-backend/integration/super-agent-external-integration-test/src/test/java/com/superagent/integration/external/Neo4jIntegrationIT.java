package com.superagent.integration.external;

import org.junit.jupiter.api.Test;
import org.neo4j.driver.AuthTokens;
import org.neo4j.driver.GraphDatabase;
import org.neo4j.driver.Record;

import static org.assertj.core.api.Assertions.assertThat;

class Neo4jIntegrationIT extends AbstractExternalIntegrationIT {

    @Test
    void shouldCreateQueryAndDeleteNeo4jGraphData() {
        ExternalServiceIntegrationProperties.AuthenticatedEndpointProperties neo4j = properties.getNeo4j();
        String runId = runId("neo4j");

        try (var driver = GraphDatabase.driver(
                neo4j.getEndpoint(),
                AuthTokens.basic(neo4j.getUsername(), neo4j.getPassword()));
             var session = driver.session()) {
            try {
                Record record = session.executeWrite(transaction -> {
                    transaction.run("""
                            CREATE (source:ExternalIntegrationTest {runId: $runId, name: 'source'})
                            CREATE (target:ExternalIntegrationTest {runId: $runId, name: 'target'})
                            CREATE (source)-[:RELATES_TO {runId: $runId}]->(target)
                            """, org.neo4j.driver.Values.parameters("runId", runId));

                    return transaction.run("""
                            MATCH (source:ExternalIntegrationTest {runId: $runId})-[relation:RELATES_TO {runId: $runId}]->(target:ExternalIntegrationTest {runId: $runId})
                            RETURN source.name AS sourceName, target.name AS targetName, count(relation) AS relationCount
                            """, org.neo4j.driver.Values.parameters("runId", runId)).single();
                });

                assertThat(record.get("sourceName").asString()).isEqualTo("source");
                assertThat(record.get("targetName").asString()).isEqualTo("target");
                assertThat(record.get("relationCount").asLong()).isEqualTo(1L);
            } finally {
                session.executeWrite(transaction -> {
                    transaction.run("""
                            MATCH (node:ExternalIntegrationTest {runId: $runId})
                            DETACH DELETE node
                            """, org.neo4j.driver.Values.parameters("runId", runId));
                    return null;
                });
            }
        }
    }
}
