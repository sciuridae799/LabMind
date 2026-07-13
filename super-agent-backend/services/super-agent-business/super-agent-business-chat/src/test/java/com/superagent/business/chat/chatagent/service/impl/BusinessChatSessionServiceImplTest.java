package com.superagent.business.chat.chatagent.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.alibaba.cloud.ai.graph.checkpoint.Checkpoint;
import com.alibaba.cloud.ai.graph.checkpoint.savers.mysql.MysqlSaver;
import com.superagent.business.chat.chatagent.persistence.data.BusinessChatDialogueData;
import com.superagent.business.chat.chatagent.api.dto.BusinessChatDeleteSessionRequest;
import com.superagent.business.chat.chatagent.persistence.mapper.BusinessChatDialogueMapper;
import com.superagent.business.chat.chatagent.persistence.mapper.BusinessChatExchangeMapper;
import com.superagent.business.chat.chatagent.persistence.mapper.BusinessChatExchangeTraceStageMapper;
import com.superagent.business.chat.chatagent.persistence.mapper.BusinessChatMemorySummaryMapper;
import com.superagent.business.chat.chatagent.persistence.mapper.BusinessChatModelCallTraceMapper;
import com.superagent.business.chat.chatagent.persistence.mapper.BusinessChatToolCallTraceMapper;
import com.superagent.business.chat.chatagent.service.BusinessChatErrorCode;
import com.superagent.business.chat.chatagent.service.BusinessChatSessionStateService;
import com.superagent.business.chat.auth.AuthRole;
import com.superagent.business.chat.auth.AuthSessionContext;
import com.superagent.business.chat.auth.AuthSessionHolder;
import com.superagent.common.frame.exception.BaseException;
import com.superagent.redisson.servicelease.lease.RedisLeaseManager;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RKeys;
import org.redisson.api.RedissonClient;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionSynchronizationUtils;

@ExtendWith(MockitoExtension.class)
class BusinessChatSessionServiceImplTest {

    @Mock
    private RedisLeaseManager redisLeaseManager;

    @Mock
    private BusinessChatDialogueMapper businessChatDialogueMapper;

    @Mock
    private BusinessChatExchangeMapper businessChatExchangeMapper;

    @Mock
    private BusinessChatMemorySummaryMapper businessChatMemorySummaryMapper;

    @Mock
    private BusinessChatExchangeTraceStageMapper businessChatExchangeTraceStageMapper;

    @Mock
    private BusinessChatModelCallTraceMapper businessChatModelCallTraceMapper;

    @Mock
    private BusinessChatToolCallTraceMapper businessChatToolCallTraceMapper;

    @Mock
    private BusinessChatSessionStateService businessChatSessionStateService;

    @Mock
    private MysqlSaver businessChatCheckpointSaver;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RKeys redissonKeys;

    @Mock
    private Checkpoint checkpoint;

    private BusinessChatSessionServiceImpl businessChatSessionService;

    @BeforeEach
    void setUp() {
        TransactionSynchronizationManager.initSynchronization();
        AuthSessionHolder.set(new AuthSessionContext(
                "token-1",
                "1001",
                "admin",
                "管理员",
                AuthRole.SUPER_ADMIN,
                "workspace-1",
                "工作组"));
        businessChatSessionService = new BusinessChatSessionServiceImpl(
                redisLeaseManager,
                businessChatDialogueMapper,
                businessChatExchangeMapper,
                businessChatMemorySummaryMapper,
                businessChatExchangeTraceStageMapper,
                businessChatModelCallTraceMapper,
                businessChatToolCallTraceMapper,
                businessChatSessionStateService,
                businessChatCheckpointSaver,
                redissonClient);
    }

    @AfterEach
    void tearDown() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);
        }
        AuthSessionHolder.clear();
    }

    @Test
    void shouldDeleteConversationArchiveAndReleaseCheckpointsWhenLeaseIsAcquired() throws Exception {
        BusinessChatDeleteSessionRequest request = new BusinessChatDeleteSessionRequest();
        request.setConversationId("conversation-1");
        request.setWorkspaceId("workspace-1");

        BusinessChatDialogueData dialogueData = new BusinessChatDialogueData();
        dialogueData.setDialogueCode("conversation-1");

        when(redisLeaseManager.acquire(any(), any(), any())).thenReturn(true);
        when(redisLeaseManager.release(any(), any())).thenReturn(true);
        when(businessChatDialogueMapper.selectOne(any())).thenReturn(dialogueData);
        when(businessChatCheckpointSaver.list(any())).thenReturn(List.of(checkpoint));
        when(redissonClient.getKeys()).thenReturn(redissonKeys);

        businessChatSessionService.deleteSession(request);

        verify(redisLeaseManager, never()).release(any(), any());
        verify(businessChatDialogueMapper).update(any(), any());
        verify(businessChatExchangeMapper).update(any(), any());
        verify(businessChatMemorySummaryMapper).update(any(), any());
        verify(businessChatExchangeTraceStageMapper).update(any(), any());
        verify(businessChatModelCallTraceMapper).update(any(), any());
        verify(businessChatToolCallTraceMapper).update(any(), any());
        verify(businessChatSessionStateService).clearIfActive("conversation-1", "workspace-1", "");
        verify(businessChatCheckpointSaver).release(argThat(config ->
                config.threadId().filter("conversation-1"::equals).isPresent()));
        verify(redissonKeys).delete(
                "super-agent:chat:model-calls:thread:conversation-1",
                "super-agent:chat:tool-calls:thread:conversation-1:tavily_search");
        completeTransaction(TransactionSynchronization.STATUS_COMMITTED);
        verify(redisLeaseManager).release(any(), any());
    }

    @Test
    void shouldDeleteConversationWithoutReleasingWhenNoGraphCheckpointExists() throws Exception {
        BusinessChatDeleteSessionRequest request = new BusinessChatDeleteSessionRequest();
        request.setConversationId("conversation-1");
        request.setWorkspaceId("workspace-1");

        BusinessChatDialogueData dialogueData = new BusinessChatDialogueData();
        dialogueData.setDialogueCode("conversation-1");

        when(redisLeaseManager.acquire(any(), any(), any())).thenReturn(true);
        when(redisLeaseManager.release(any(), any())).thenReturn(true);
        when(businessChatDialogueMapper.selectOne(any())).thenReturn(dialogueData);
        when(businessChatCheckpointSaver.list(any())).thenReturn(List.of());
        when(redissonClient.getKeys()).thenReturn(redissonKeys);

        businessChatSessionService.deleteSession(request);

        verify(businessChatCheckpointSaver, never()).release(any());
        verify(redisLeaseManager, never()).release(any(), any());
        completeTransaction(TransactionSynchronization.STATUS_COMMITTED);
        verify(redisLeaseManager).release(any(), any());
    }

    @Test
    void shouldPropagateCheckpointReleaseFailureAndReleaseLease() throws Exception {
        BusinessChatDeleteSessionRequest request = new BusinessChatDeleteSessionRequest();
        request.setConversationId("conversation-1");
        request.setWorkspaceId("workspace-1");

        BusinessChatDialogueData dialogueData = new BusinessChatDialogueData();
        dialogueData.setDialogueCode("conversation-1");

        when(redisLeaseManager.acquire(any(), any(), any())).thenReturn(true);
        when(redisLeaseManager.release(any(), any())).thenReturn(true);
        when(businessChatDialogueMapper.selectOne(any())).thenReturn(dialogueData);
        when(businessChatCheckpointSaver.list(any())).thenReturn(List.of(checkpoint));
        when(businessChatCheckpointSaver.release(any())).thenThrow(new Exception("release failed"));

        assertThatThrownBy(() -> businessChatSessionService.deleteSession(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("failed to release graph checkpoints for conversation: conversation-1")
                .hasRootCauseMessage("release failed");

        verify(redisLeaseManager, never()).release(any(), any());
        completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(redisLeaseManager).release(any(), any());
    }

    @Test
    void shouldRejectDeleteWhenConversationIsRunning() {
        BusinessChatDeleteSessionRequest request = new BusinessChatDeleteSessionRequest();
        request.setConversationId("conversation-1");
        request.setWorkspaceId("workspace-1");

        when(redisLeaseManager.acquire(any(), any(), any())).thenReturn(false);

        assertThatThrownBy(() -> businessChatSessionService.deleteSession(request))
                .isInstanceOf(BaseException.class)
                .extracting("code")
                .isEqualTo(BusinessChatErrorCode.CHAT_SESSION_RUNNING.getCode());

        verify(businessChatDialogueMapper, never()).selectOne(any());
        verify(businessChatCheckpointSaver, never()).list(any());
        verify(redisLeaseManager, never()).release(any(), any());
    }

    @Test
    void shouldThrowWhenConversationDoesNotExist() {
        BusinessChatDeleteSessionRequest request = new BusinessChatDeleteSessionRequest();
        request.setConversationId("conversation-missing");
        request.setWorkspaceId("workspace-1");

        when(redisLeaseManager.acquire(any(), any(), any())).thenReturn(true);
        when(redisLeaseManager.release(any(), any())).thenReturn(true);
        when(businessChatDialogueMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> businessChatSessionService.deleteSession(request))
                .isInstanceOf(BaseException.class)
                .extracting("code")
                .isEqualTo(BusinessChatErrorCode.CHAT_SESSION_NOT_FOUND.getCode());

        verify(redisLeaseManager, never()).release(any(), any());
        completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);
        verify(redisLeaseManager).release(any(), any());
        verify(businessChatExchangeMapper, never()).update(any(), any());
        verify(businessChatModelCallTraceMapper, never()).update(any(), any());
        verify(businessChatToolCallTraceMapper, never()).update(any(), any());
        verify(businessChatCheckpointSaver, never()).list(any());
    }

    @Test
    void shouldReleaseLeaseAndFailWhenTransactionSynchronizationIsMissing() {
        completeTransaction(TransactionSynchronization.STATUS_ROLLED_BACK);
        BusinessChatDeleteSessionRequest request = new BusinessChatDeleteSessionRequest();
        request.setConversationId("conversation-1");
        request.setWorkspaceId("workspace-1");
        when(redisLeaseManager.acquire(any(), any(), any())).thenReturn(true);
        when(redisLeaseManager.release(any(), any())).thenReturn(true);

        assertThatThrownBy(() -> businessChatSessionService.deleteSession(request))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("transaction synchronization is required for conversation deletion");

        verify(redisLeaseManager).release(any(), any());
        verify(businessChatDialogueMapper, never()).selectOne(any());
    }

    private void completeTransaction(int completionStatus) {
        TransactionSynchronizationUtils.invokeAfterCompletion(
                TransactionSynchronizationManager.getSynchronizations(),
                completionStatus);
        TransactionSynchronizationManager.clearSynchronization();
    }
}
