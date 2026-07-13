package com.labmind.business.chat.chatagent.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.labmind.business.chat.auth.AuthErrorCode;
import com.labmind.business.chat.auth.AuthException;
import com.labmind.business.chat.chatagent.orchestration.model.BusinessChatMode;
import com.labmind.business.chat.chatagent.orchestration.model.BusinessChatStartPlan;
import com.labmind.business.chat.chatagent.persistence.data.BusinessChatDialogueData;
import com.labmind.business.chat.chatagent.persistence.data.BusinessChatExchangeData;
import com.labmind.business.chat.chatagent.persistence.data.BusinessChatExchangeTraceStageData;
import com.labmind.business.chat.chatagent.persistence.data.BusinessChatModelCallTraceData;
import com.labmind.business.chat.chatagent.persistence.data.BusinessChatToolCallTraceData;
import com.labmind.business.chat.chatagent.persistence.mapper.BusinessChatDialogueMapper;
import com.labmind.business.chat.chatagent.persistence.mapper.BusinessChatExchangeMapper;
import com.labmind.business.chat.chatagent.persistence.mapper.BusinessChatExchangeTraceStageMapper;
import com.labmind.business.chat.chatagent.persistence.mapper.BusinessChatMemorySummaryMapper;
import com.labmind.business.chat.chatagent.persistence.mapper.BusinessChatModelCallTraceMapper;
import com.labmind.business.chat.chatagent.persistence.mapper.BusinessChatToolCallTraceMapper;
import com.labmind.business.chat.chatagent.persistence.model.BusinessChatExchangeState;
import com.labmind.business.chat.chatagent.runtime.BusinessChatRuntimeContext;
import com.labmind.business.chat.chatagent.runtime.BusinessChatTaskInfo;
import com.labmind.business.chat.chatagent.service.BusinessChatSessionStateService;
import com.labmind.idgenerator.toolkit.SnowflakeIdGenerator;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Sinks;

@ExtendWith(MockitoExtension.class)
class BusinessChatPersistenceServiceImplTest {

    @Mock
    private BusinessChatDialogueMapper dialogueMapper;

    @Mock
    private BusinessChatExchangeMapper exchangeMapper;

    @Mock
    private BusinessChatExchangeTraceStageMapper traceStageMapper;

    @Mock
    private BusinessChatMemorySummaryMapper memorySummaryMapper;

    @Mock
    private BusinessChatModelCallTraceMapper modelCallTraceMapper;

    @Mock
    private BusinessChatToolCallTraceMapper toolCallTraceMapper;

    @Mock
    private BusinessChatSessionStateService sessionStateService;

    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private BusinessChatPersistenceServiceImpl service;

    @BeforeAll
    static void initializeMybatisMetadata() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        assistant.setCurrentNamespace(BusinessChatPersistenceServiceImplTest.class.getName());
        TableInfoHelper.initTableInfo(assistant, BusinessChatDialogueData.class);
        TableInfoHelper.initTableInfo(assistant, BusinessChatExchangeTraceStageData.class);
        TableInfoHelper.initTableInfo(assistant, BusinessChatModelCallTraceData.class);
        TableInfoHelper.initTableInfo(assistant, BusinessChatToolCallTraceData.class);
    }

    @Test
    void shouldRejectConversationIdOwnedByAnotherGuestSession() {
        BusinessChatDialogueData existingDialogue = new BusinessChatDialogueData();
        existingDialogue.setId(1001L);
        existingDialogue.setDialogueCode("shared-conversation");
        existingDialogue.setWorkspaceId("public-demo");
        existingDialogue.setAuthSessionToken("guest-token-a");
        existingDialogue.setStatus(1);
        when(dialogueMapper.selectList(any())).thenReturn(List.of(existingDialogue));

        assertThatThrownBy(() -> service.createTurnRecordAndBuildTaskInfo(startPlan("guest-token-b")))
                .isInstanceOf(AuthException.class)
                .extracting("code")
                .isEqualTo(AuthErrorCode.AUTH_FORBIDDEN.getCode());

        verify(dialogueMapper, never()).insert(any(BusinessChatDialogueData.class));
        verify(exchangeMapper, never()).insert(any(BusinessChatExchangeData.class));
    }

    @Test
    void shouldFinalizeRunningTraceRecordsBeforeArchivingStoppedTurn() {
        LocalDateTime startTime = LocalDateTime.now().minusSeconds(2);
        BusinessChatExchangeTraceStageData runningStage = new BusinessChatExchangeTraceStageData();
        runningStage.setId(3001L);
        runningStage.setStartTime(startTime);
        BusinessChatModelCallTraceData runningModelCall = new BusinessChatModelCallTraceData();
        runningModelCall.setId(4001L);
        runningModelCall.setStartTime(startTime);
        BusinessChatToolCallTraceData runningToolCall = new BusinessChatToolCallTraceData();
        runningToolCall.setId(5001L);
        runningToolCall.setStartTime(startTime);
        when(traceStageMapper.selectList(any())).thenReturn(List.of(runningStage));
        when(modelCallTraceMapper.selectList(any())).thenReturn(List.of(runningModelCall));
        when(toolCallTraceMapper.selectList(any())).thenReturn(List.of(runningToolCall));
        when(traceStageMapper.updateById(any(BusinessChatExchangeTraceStageData.class))).thenReturn(1);
        when(modelCallTraceMapper.updateById(any(BusinessChatModelCallTraceData.class))).thenReturn(1);
        when(toolCallTraceMapper.updateById(any(BusinessChatToolCallTraceData.class))).thenReturn(1);

        service.archiveStoppedTurn(runtimeContext(), "本轮回答已中止");

        ArgumentCaptor<BusinessChatExchangeTraceStageData> stageCaptor =
                ArgumentCaptor.forClass(BusinessChatExchangeTraceStageData.class);
        ArgumentCaptor<BusinessChatModelCallTraceData> modelCallCaptor =
                ArgumentCaptor.forClass(BusinessChatModelCallTraceData.class);
        ArgumentCaptor<BusinessChatToolCallTraceData> toolCallCaptor =
                ArgumentCaptor.forClass(BusinessChatToolCallTraceData.class);
        ArgumentCaptor<BusinessChatExchangeData> exchangeCaptor =
                ArgumentCaptor.forClass(BusinessChatExchangeData.class);
        verify(traceStageMapper).updateById(stageCaptor.capture());
        verify(modelCallTraceMapper).updateById(modelCallCaptor.capture());
        verify(toolCallTraceMapper).updateById(toolCallCaptor.capture());
        verify(exchangeMapper).updateById(exchangeCaptor.capture());

        assertFailedTrace(stageCaptor.getValue().getStageState(), stageCaptor.getValue().getErrorMessage(),
                stageCaptor.getValue().getDurationMs());
        assertFailedTrace(modelCallCaptor.getValue().getCallState(), modelCallCaptor.getValue().getErrorMessage(),
                modelCallCaptor.getValue().getDurationMs());
        assertFailedTrace(toolCallCaptor.getValue().getCallState(), toolCallCaptor.getValue().getErrorMessage(),
                toolCallCaptor.getValue().getDurationMs());
        assertThat(exchangeCaptor.getValue().getExchangeState())
                .isEqualTo(BusinessChatExchangeState.STOPPED.getDatabaseCode());
        assertThat(exchangeCaptor.getValue().getFinishNote()).isEqualTo("本轮回答已中止");

        InOrder updateOrder = inOrder(traceStageMapper, modelCallTraceMapper, toolCallTraceMapper, exchangeMapper);
        updateOrder.verify(traceStageMapper).updateById(any(BusinessChatExchangeTraceStageData.class));
        updateOrder.verify(modelCallTraceMapper).updateById(any(BusinessChatModelCallTraceData.class));
        updateOrder.verify(toolCallTraceMapper).updateById(any(BusinessChatToolCallTraceData.class));
        updateOrder.verify(exchangeMapper).updateById(any(BusinessChatExchangeData.class));
    }

    private void assertFailedTrace(Integer state, String errorMessage, Long durationMs) {
        assertThat(state).isEqualTo(3);
        assertThat(errorMessage).isEqualTo("本轮回答已中止");
        assertThat(durationMs).isPositive();
    }

    private BusinessChatRuntimeContext runtimeContext() {
        BusinessChatTaskInfo taskInfo = new BusinessChatTaskInfo(
                1001L,
                2001L,
                "question",
                "conversation-1",
                "workspace-1",
                "",
                BusinessChatMode.OPEN_ENDED,
                null,
                null,
                null,
                "trace-1",
                "lease-key",
                "lease-owner",
                Duration.ofMinutes(1),
                System.currentTimeMillis());
        return new BusinessChatRuntimeContext(taskInfo, Sinks.many().unicast().onBackpressureBuffer());
    }

    private BusinessChatStartPlan startPlan(String authSessionToken) {
        return new BusinessChatStartPlan(
                "question",
                "shared-conversation",
                "public-demo",
                authSessionToken,
                BusinessChatMode.OPEN_ENDED,
                null,
                null,
                null,
                "trace-1",
                "lease-key",
                "lease-owner",
                Duration.ofMinutes(1),
                1L);
    }
}
