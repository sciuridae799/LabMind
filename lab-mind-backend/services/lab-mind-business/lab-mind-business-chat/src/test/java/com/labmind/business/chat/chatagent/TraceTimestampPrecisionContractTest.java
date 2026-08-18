package com.labmind.business.chat.chatagent;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class TraceTimestampPrecisionContractTest {

    @Test
    void shouldPreserveMillisecondsForTraceDurations() throws IOException {
        String schema = readRepositoryFile("sql/lab-mind-business-chat/mysql/init/002_create_chat_tables.sql");
        String migration = readRepositoryFile(
                "sql/lab-mind-business-chat/mysql/migration/021_preserve_trace_timestamp_milliseconds.sql");

        assertThat(occurrences(schema, "start_time DATETIME(3)")).isEqualTo(3);
        assertThat(occurrences(schema, "end_time DATETIME(3)")).isEqualTo(3);
        assertThat(occurrences(migration, "MODIFY COLUMN start_time DATETIME(3)")).isEqualTo(3);
        assertThat(occurrences(migration, "MODIFY COLUMN end_time DATETIME(3)")).isEqualTo(3);
        assertThat(occurrences(migration, "SET duration_ms = 0")).isEqualTo(3);
    }

    private String readRepositoryFile(String relativePath) throws IOException {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relativePath);
            if (Files.isRegularFile(candidate)) {
                return Files.readString(candidate);
            }
            current = current.getParent();
        }
        throw new IOException("repository file not found: " + relativePath);
    }

    private long occurrences(String source, String value) {
        return Pattern.compile(Pattern.quote(value)).matcher(source).results().count();
    }
}
