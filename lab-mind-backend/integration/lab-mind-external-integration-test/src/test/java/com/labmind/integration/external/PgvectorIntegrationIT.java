package com.labmind.integration.external;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PgvectorIntegrationIT extends AbstractExternalIntegrationIT {

    @Test
    void shouldQueryVectorDistanceFromPgvector() throws Exception {
        ExternalServiceIntegrationProperties.JdbcServiceProperties pgvector = properties.getPgvector();
        String tableName = "integration_pgvector_" + runId("case").replace("-", "");

        try (Connection connection = DriverManager.getConnection(
                pgvector.getJdbcUrl(),
                pgvector.getUsername(),
                pgvector.getPassword())) {
            try (PreparedStatement extensionStatement =
                         connection.prepareStatement("SELECT extname FROM pg_extension WHERE extname = 'vector'");
                 ResultSet extensionResult = extensionStatement.executeQuery()) {
                assertThat(extensionResult.next()).isTrue();
                assertThat(extensionResult.getString("extname")).isEqualTo("vector");
            }

            try (Statement statement = connection.createStatement()) {
                statement.execute("CREATE TEMP TABLE " + tableName + " (id TEXT PRIMARY KEY, embedding vector(3) NOT NULL)");
                statement.execute("""
                        INSERT INTO %s (id, embedding)
                        VALUES ('near', '[1,1,1]'), ('far', '[9,9,9]')
                        """.formatted(tableName));
            }

            try (PreparedStatement queryStatement = connection.prepareStatement("""
                    SELECT id
                    FROM %s
                    ORDER BY embedding <-> CAST(? AS vector)
                    LIMIT 1
                    """.formatted(tableName))) {
                queryStatement.setString(1, "[1.05,1.02,0.98]");
                try (ResultSet resultSet = queryStatement.executeQuery()) {
                    assertThat(resultSet.next()).isTrue();
                    assertThat(resultSet.getString("id")).isEqualTo("near");
                    assertThat(resultSet.next()).isFalse();
                }
            }
        }
    }
}
