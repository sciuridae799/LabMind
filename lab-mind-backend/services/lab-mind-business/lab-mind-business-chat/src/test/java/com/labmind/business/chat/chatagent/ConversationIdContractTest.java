package com.labmind.business.chat.chatagent;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.labmind.business.chat.chatagent.api.dto.BusinessChatStreamRequest;
import com.labmind.business.chat.chatagent.orchestration.model.BusinessChatMode;
import com.labmind.business.chat.chatagent.runtime.BusinessChatConversationLeaseKeys;
import com.labmind.business.chat.chatagent.runtime.BusinessChatTaskInfo;
import com.labmind.business.chat.chatagent.runtime.InMemoryBusinessChatRuntimeRegistry;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class ConversationIdContractTest {

    private static final String BINARY_CONVERSATION_ID =
            "VARCHAR(64) COLLATE utf8mb4_bin";

    @Test
    void shouldKeepApiConversationIdWithin64Characters() {
        try (ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = validatorFactory.getValidator();
            BusinessChatStreamRequest request = new BusinessChatStreamRequest();

            request.setConversationId("A".repeat(64));
            assertThat(validator.validateProperty(request, "conversationId")).isEmpty();

            request.setConversationId("A".repeat(65));
            assertThat(validator.validateProperty(request, "conversationId"))
                    .singleElement()
                    .extracting(violation -> violation.getMessage())
                    .isEqualTo("conversationId length must be less than or equal to 64");
        }
    }

    @Test
    void shouldUseCaseSensitiveRuntimeIdentity() {
        String upperCaseId = "Conversation-A";
        String lowerCaseId = "conversation-a";

        assertThat(BusinessChatConversationLeaseKeys.conversationLeaseKey(upperCaseId))
                .isNotEqualTo(BusinessChatConversationLeaseKeys.conversationLeaseKey(lowerCaseId));

        InMemoryBusinessChatRuntimeRegistry registry = new InMemoryBusinessChatRuntimeRegistry();
        assertThatCode(() -> {
                    registry.register(taskInfo(upperCaseId));
                    registry.register(taskInfo(lowerCaseId));
                })
                .doesNotThrowAnyException();
    }

    @Test
    void shouldUseCaseSensitiveMysqlConversationIdentity() throws IOException {
        String chatSchema = readRepositoryFile("sql/lab-mind-business-chat/mysql/init/002_create_chat_tables.sql");
        String routeSchema = readRepositoryFile(
                "sql/lab-mind-business-chat/mysql/init/007_create_knowledge_route_asset_tables.sql");
        String migration = readRepositoryFile(
                "sql/lab-mind-business-chat/mysql/migration/020_enforce_conversation_id_contract.sql");

        assertThat(occurrences(chatSchema, "dialogue_code " + BINARY_CONVERSATION_ID)).isEqualTo(6);
        assertThat(chatSchema).contains("active_conversation_id " + BINARY_CONVERSATION_ID);
        assertThat(routeSchema).contains("conversation_id " + BINARY_CONVERSATION_ID);
        assertThat(occurrences(migration, "dialogue_code " + BINARY_CONVERSATION_ID)).isEqualTo(6);
        assertThat(migration)
                .contains("SET exchange_data.dialogue_code = dialogue_data.dialogue_code")
                .contains("SET summary_data.dialogue_code = dialogue_data.dialogue_code")
                .contains("SET trace_stage_data.dialogue_code = dialogue_data.dialogue_code")
                .contains("SET model_trace_data.dialogue_code = dialogue_data.dialogue_code")
                .contains("SET tool_trace_data.dialogue_code = dialogue_data.dialogue_code")
                .contains("SET session_state_data.active_conversation_id = dialogue_data.dialogue_code")
                .contains("SET route_trace_data.conversation_id = dialogue_data.dialogue_code")
                .contains("active_conversation_id " + BINARY_CONVERSATION_ID)
                .contains("conversation_id " + BINARY_CONVERSATION_ID);
        assertThat(migration.indexOf("SET exchange_data.dialogue_code = dialogue_data.dialogue_code"))
                .isLessThan(migration.indexOf("ALTER TABLE lab_mind_chat_dialogue"));
    }

    @Test
    void shouldStoreConversationIdInGraphThreadName() throws IOException {
        String graphSchema = readRepositoryFile("sql/lab-mind-business-chat/mysql/init/003_create_graph_tables.sql");
        String migration = readRepositoryFile(
                "sql/lab-mind-business-chat/mysql/migration/020_enforce_conversation_id_contract.sql");

        assertThat(graphSchema)
                .contains("thread_id VARCHAR(36) NOT NULL")
                .contains("thread_name " + BINARY_CONVERSATION_ID + " NOT NULL")
                .contains("thread_id VARCHAR(36) NOT NULL", "thread_name " + BINARY_CONVERSATION_ID);
        assertThat(migration)
                .contains("SET graph_thread_data.thread_name = dialogue_data.dialogue_code")
                .contains("MODIFY COLUMN thread_name " + BINARY_CONVERSATION_ID + " NOT NULL")
                .doesNotContain("MODIFY COLUMN thread_id");
    }

    private BusinessChatTaskInfo taskInfo(String conversationId) {
        return new BusinessChatTaskInfo(
                1L,
                2L,
                "question",
                conversationId,
                "workspace-1",
                "",
                BusinessChatMode.OPEN_ENDED,
                null,
                null,
                null,
                "trace-1",
                BusinessChatConversationLeaseKeys.conversationLeaseKey(conversationId),
                "owner-1",
                Duration.ofSeconds(30),
                1L);
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
