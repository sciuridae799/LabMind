package com.labmind.integration.external;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MySqlIntegrationIT extends AbstractExternalIntegrationIT {

    @Test
    void shouldReadWriteWithinMySqlTransaction() throws Exception {
        ExternalServiceIntegrationProperties.JdbcServiceProperties mysql = properties.getMysql();
        String tableName = "integration_mysql_" + runId("case").replace("-", "");
        String payload = runId("payload");

        try (Connection connection = DriverManager.getConnection(
                mysql.getJdbcUrl(),
                mysql.getUsername(),
                mysql.getPassword())) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.execute("""
                        CREATE TEMPORARY TABLE %s (
                            id BIGINT PRIMARY KEY AUTO_INCREMENT,
                            payload VARCHAR(255) NOT NULL
                        )
                        """.formatted(tableName));
            }

            try (PreparedStatement insertStatement =
                         connection.prepareStatement("INSERT INTO " + tableName + " (payload) VALUES (?)")) {
                insertStatement.setString(1, payload);
                assertThat(insertStatement.executeUpdate()).isEqualTo(1);
            }

            connection.commit();

            try (PreparedStatement queryStatement =
                         connection.prepareStatement("SELECT payload FROM " + tableName + " WHERE payload = ?")) {
                queryStatement.setString(1, payload);
                try (ResultSet resultSet = queryStatement.executeQuery()) {
                    assertThat(resultSet.next()).isTrue();
                    assertThat(resultSet.getString("payload")).isEqualTo(payload);
                    assertThat(resultSet.next()).isFalse();
                }
            }
        }
    }
}
