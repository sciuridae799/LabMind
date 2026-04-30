package com.superagent.business.chat.chatagent.service.impl;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.superagent.business.chat.chatagent.persistence.data.BusinessChatDialogueData;
import com.superagent.business.chat.chatagent.api.dto.BusinessChatDeleteSessionRequest;
import com.superagent.business.chat.chatagent.persistence.mapper.BusinessChatDialogueMapper;
import com.superagent.business.chat.chatagent.persistence.mapper.BusinessChatExchangeMapper;
import com.superagent.business.chat.chatagent.persistence.mapper.BusinessChatExchangeTraceStageMapper;
import com.superagent.business.chat.chatagent.persistence.mapper.BusinessChatMemorySummaryMapper;
import com.superagent.business.chat.chatagent.service.BusinessChatErrorCode;
import com.superagent.business.chat.chatagent.service.BusinessChatSessionStateService;
import com.superagent.common.frame.exception.BaseException;
import com.superagent.redisson.servicelease.lease.RedisLeaseManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
    private BusinessChatSessionStateService businessChatSessionStateService;

    private BusinessChatSessionServiceImpl businessChatSessionService;

    @BeforeEach
    void setUp() {
        businessChatSessionService = new BusinessChatSessionServiceImpl(
                redisLeaseManager,
                businessChatDialogueMapper,
                businessChatExchangeMapper,
                businessChatMemorySummaryMapper,
                businessChatExchangeTraceStageMapper,
                businessChatSessionStateService);
    }

    @Test
    void shouldDeleteConversationArchiveWhenLeaseIsAcquired() {
        BusinessChatDeleteSessionRequest request = new BusinessChatDeleteSessionRequest();
        request.setConversationId("conversation-1");

        BusinessChatDialogueData dialogueData = new BusinessChatDialogueData();
        dialogueData.setDialogueCode("conversation-1");

        when(redisLeaseManager.acquire(any(), any(), any())).thenReturn(true);
        when(redisLeaseManager.release(any(), any())).thenReturn(true);
        when(businessChatDialogueMapper.selectOne(any())).thenReturn(dialogueData);

        businessChatSessionService.deleteSession(request);

        verify(businessChatDialogueMapper).update(any(), any());
        verify(businessChatExchangeMapper).update(any(), any());
        verify(businessChatMemorySummaryMapper).update(any(), any());
        verify(businessChatExchangeTraceStageMapper).update(any(), any());
        verify(businessChatSessionStateService).clearIfActive("conversation-1");
        verify(redisLeaseManager).release(any(), any());
    }

    @Test
    void shouldRejectDeleteWhenConversationIsRunning() {
        BusinessChatDeleteSessionRequest request = new BusinessChatDeleteSessionRequest();
        request.setConversationId("conversation-1");

        when(redisLeaseManager.acquire(any(), any(), any())).thenReturn(false);

        assertThatThrownBy(() -> businessChatSessionService.deleteSession(request))
                .isInstanceOf(BaseException.class)
                .extracting("code")
                .isEqualTo(BusinessChatErrorCode.CHAT_SESSION_RUNNING.getCode());

        verify(businessChatDialogueMapper, never()).selectOne(any());
        verify(redisLeaseManager, never()).release(any(), any());
    }

    @Test
    void shouldThrowWhenConversationDoesNotExist() {
        BusinessChatDeleteSessionRequest request = new BusinessChatDeleteSessionRequest();
        request.setConversationId("conversation-missing");

        when(redisLeaseManager.acquire(any(), any(), any())).thenReturn(true);
        when(redisLeaseManager.release(any(), any())).thenReturn(true);
        when(businessChatDialogueMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> businessChatSessionService.deleteSession(request))
                .isInstanceOf(BaseException.class)
                .extracting("code")
                .isEqualTo(BusinessChatErrorCode.CHAT_SESSION_NOT_FOUND.getCode());

        verify(redisLeaseManager).release(any(), any());
        verify(businessChatExchangeMapper, never()).update(any(), any());
    }
}
