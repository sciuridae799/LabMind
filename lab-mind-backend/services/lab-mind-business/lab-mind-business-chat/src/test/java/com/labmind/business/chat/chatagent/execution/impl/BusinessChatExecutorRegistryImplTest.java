package com.labmind.business.chat.chatagent.execution.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.labmind.business.chat.chatagent.orchestration.model.BusinessChatMode;
import com.labmind.business.chat.chatagent.execution.BusinessChatExecutor;
import com.labmind.common.frame.exception.BaseException;
import java.util.List;
import org.junit.jupiter.api.Test;

class BusinessChatExecutorRegistryImplTest {

    @Test
    void shouldReturnExecutorByExecutionMode() {
        BusinessChatExecutor openEndedExecutor = mockExecutor(BusinessChatMode.OPEN_ENDED);
        BusinessChatExecutor knowledgeBaseExecutor = mockExecutor(BusinessChatMode.KNOWLEDGE_BASE);
        BusinessChatExecutorRegistryImpl registry =
                new BusinessChatExecutorRegistryImpl(List.of(openEndedExecutor, knowledgeBaseExecutor));

        assertThat(registry.getRequiredExecutor(BusinessChatMode.OPEN_ENDED)).isSameAs(openEndedExecutor);
        assertThat(registry.getRequiredExecutor(BusinessChatMode.KNOWLEDGE_BASE)).isSameAs(knowledgeBaseExecutor);
    }

    @Test
    void shouldRejectDuplicateExecutionModeAtRegistration() {
        BusinessChatExecutor firstExecutor = mockExecutor(BusinessChatMode.OPEN_ENDED);
        BusinessChatExecutor secondExecutor = mockExecutor(BusinessChatMode.OPEN_ENDED);

        assertThatThrownBy(() -> new BusinessChatExecutorRegistryImpl(List.of(firstExecutor, secondExecutor)))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("Duplicate executor for execution mode: OPEN_ENDED");
    }

    @Test
    void shouldRejectMissingExecutionModeAtLookup() {
        BusinessChatExecutor openEndedExecutor = mockExecutor(BusinessChatMode.OPEN_ENDED);
        BusinessChatExecutorRegistryImpl registry = new BusinessChatExecutorRegistryImpl(List.of(openEndedExecutor));

        assertThatThrownBy(() -> registry.getRequiredExecutor(BusinessChatMode.KNOWLEDGE_BASE))
                .isInstanceOf(BaseException.class)
                .hasMessageContaining("No executor supports execution mode: KNOWLEDGE_BASE");
    }

    private BusinessChatExecutor mockExecutor(BusinessChatMode executionMode) {
        BusinessChatExecutor executor = mock(BusinessChatExecutor.class);
        when(executor.executionMode()).thenReturn(executionMode);
        return executor;
    }
}
