package com.labmind.business.chat.chatagent.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.labmind.business.chat.chatagent.persistence.data.BusinessChatSessionStateData;
import com.labmind.business.chat.chatagent.persistence.mapper.BusinessChatDialogueMapper;
import com.labmind.business.chat.chatagent.persistence.mapper.BusinessChatSessionStateMapper;
import com.labmind.idgenerator.toolkit.SnowflakeIdGenerator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BusinessChatSessionStateServiceImplTest {

    @Mock
    private BusinessChatSessionStateMapper sessionStateMapper;

    @Mock
    private BusinessChatDialogueMapper dialogueMapper;

    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;

    private BusinessChatSessionStateServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new BusinessChatSessionStateServiceImpl(sessionStateMapper, dialogueMapper, snowflakeIdGenerator);
    }

    @Test
    void shouldActivateChatPageStateWithOneAtomicUpsert() {
        when(snowflakeIdGenerator.nextId()).thenReturn(1001L);

        service.activate(" conversation-1 ", " workspace-1 ", " guest-token-1 ");

        ArgumentCaptor<BusinessChatSessionStateData> stateCaptor =
                ArgumentCaptor.forClass(BusinessChatSessionStateData.class);
        verify(sessionStateMapper).upsertActiveConversation(stateCaptor.capture());
        BusinessChatSessionStateData state = stateCaptor.getValue();
        assertThat(state.getId()).isEqualTo(1001L);
        assertThat(state.getStateKey()).isEqualTo("CHAT_PAGE");
        assertThat(state.getWorkspaceId()).isEqualTo("workspace-1");
        assertThat(state.getAuthSessionToken()).isEqualTo("guest-token-1");
        assertThat(state.getActiveConversationId()).isEqualTo("conversation-1");
        assertThat(state.getStatus()).isEqualTo(1);
        verify(sessionStateMapper, never()).selectOne(any());
        verify(sessionStateMapper, never()).insert(any(BusinessChatSessionStateData.class));
        verify(sessionStateMapper, never()).updateById(any(BusinessChatSessionStateData.class));
    }
}
