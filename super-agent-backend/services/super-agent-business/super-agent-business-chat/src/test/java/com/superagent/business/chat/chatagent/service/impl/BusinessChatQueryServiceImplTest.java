package com.superagent.business.chat.chatagent.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.superagent.business.chat.chatagent.persistence.data.BusinessChatDialogueData;
import com.superagent.business.chat.chatagent.persistence.data.BusinessChatExchangeData;
import com.superagent.business.chat.chatagent.persistence.data.BusinessChatMemorySummaryData;
import com.superagent.business.chat.chatagent.config.BusinessChatRuntimeProperties;
import com.superagent.business.chat.chatagent.api.dto.BusinessChatSessionDetailRequest;
import com.superagent.business.chat.chatagent.api.dto.BusinessChatSessionListRequest;
import com.superagent.business.chat.chatagent.persistence.mapper.BusinessChatDialogueMapper;
import com.superagent.business.chat.chatagent.persistence.mapper.BusinessChatExchangeMapper;
import com.superagent.business.chat.chatagent.persistence.mapper.BusinessChatExchangeTraceStageMapper;
import com.superagent.business.chat.chatagent.persistence.mapper.BusinessChatModelCallTraceMapper;
import com.superagent.business.chat.chatagent.persistence.mapper.BusinessChatMemorySummaryMapper;
import com.superagent.business.chat.chatagent.persistence.mapper.BusinessChatToolCallTraceMapper;
import com.superagent.business.chat.chatagent.persistence.model.BusinessChatSessionListRow;
import com.superagent.business.chat.chatagent.service.BusinessChatErrorCode;
import com.superagent.business.chat.chatagent.service.BusinessChatSessionStateService;
import com.superagent.business.chat.auth.AuthRole;
import com.superagent.business.chat.auth.AuthSessionContext;
import com.superagent.business.chat.auth.AuthSessionHolder;
import com.superagent.common.frame.exception.BaseException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BusinessChatQueryServiceImplTest {

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

    private BusinessChatQueryServiceImpl businessChatQueryService;

    @BeforeEach
    void setUp() {
        AuthSessionHolder.set(new AuthSessionContext(
                "token-1",
                "1001",
                "admin",
                "管理员",
                AuthRole.SUPER_ADMIN,
                "workspace-1",
                "工作组"));
        businessChatQueryService = new BusinessChatQueryServiceImpl(
                businessChatDialogueMapper,
                businessChatExchangeMapper,
                businessChatMemorySummaryMapper,
                businessChatExchangeTraceStageMapper,
                businessChatModelCallTraceMapper,
                businessChatToolCallTraceMapper,
                businessChatSessionStateService,
                new BusinessChatRuntimeProperties(),
                new ObjectMapper());
    }

    @AfterEach
    void tearDown() {
        AuthSessionHolder.clear();
    }

    @Test
    void shouldReturnMappedSessionPage() {
        BusinessChatSessionListRequest request = new BusinessChatSessionListRequest();
        request.setKeyword("链路");
        request.setWorkspaceId("workspace-1");
        request.setChatMode("ALL");
        request.setTurnStatus("ALL");
        request.setPageNo("1");
        request.setPageSize("20");

        BusinessChatSessionListRow row = new BusinessChatSessionListRow();
        row.setConversationId("conversation-1");
        row.setTitle("链路执行过程");
        row.setChatModeCode(3);
        row.setTurnStatusCode(2);
        row.setLastExchangeId(1001L);
        row.setLastQuestion("请说明这条链路的执行过程");
        row.setLastReply("这条链路会先编排再执行");
        row.setUpdateTime(LocalDateTime.of(2026, 4, 23, 11, 30, 0));

        when(businessChatDialogueMapper.countSessionPageRows("workspace-1", "", "链路", null, null, 1)).thenReturn(1L);
        when(businessChatDialogueMapper.selectSessionPageRows("workspace-1", "", "链路", null, null, 1, 0, 20))
                .thenReturn(List.of(row));

        var pageVo = businessChatQueryService.listSessionsPage(request);

        assertThat(pageVo.getPageNo()).isEqualTo(1);
        assertThat(pageVo.getPageSize()).isEqualTo(20);
        assertThat(pageVo.getTotalSize()).isEqualTo(1);
        assertThat(pageVo.getTotalPages()).isEqualTo(1);
        assertThat(pageVo.getSessions()).hasSize(1);
        assertThat(pageVo.getSessions().getFirst().getConversationId()).isEqualTo("conversation-1");
        assertThat(pageVo.getSessions().getFirst().getTitle()).isEqualTo("链路执行过程");
        assertThat(pageVo.getSessions().getFirst().getChatMode()).isEqualTo("OPEN_ENDED");
        assertThat(pageVo.getSessions().getFirst().getTurnStatus()).isEqualTo("COMPLETED");
    }

    @Test
    void shouldScopeGuestSessionPageByCurrentAuthToken() {
        AuthSessionHolder.set(new AuthSessionContext(
                "guest-token-1",
                "guest",
                "guest",
                "访客",
                AuthRole.GUEST,
                "public-demo",
                "访客体验资料库"));
        BusinessChatSessionListRequest request = new BusinessChatSessionListRequest();
        request.setWorkspaceId("public-demo");
        request.setChatMode("ALL");
        request.setTurnStatus("ALL");
        request.setPageNo("1");
        request.setPageSize("20");

        when(businessChatDialogueMapper.countSessionPageRows("public-demo", "guest-token-1", null, null, null, 1))
                .thenReturn(0L);

        var pageVo = businessChatQueryService.listSessionsPage(request);

        assertThat(pageVo.getTotalSize()).isZero();
        assertThat(pageVo.getSessions()).isEmpty();
    }

    @Test
    void shouldReturnSessionDetailWithExchangeHistory() {
        BusinessChatSessionDetailRequest request = new BusinessChatSessionDetailRequest();
        request.setConversationId("conversation-1");

        BusinessChatDialogueData dialogueData = new BusinessChatDialogueData();
        dialogueData.setDialogueCode("conversation-1");
        dialogueData.setDialogueTitle("链路说明");
        dialogueData.setDialogueStage(1);
        dialogueData.setChatMode(3);

        BusinessChatExchangeData exchangeData = new BusinessChatExchangeData();
        exchangeData.setId(2001L);
        exchangeData.setUserPrompt("请说明这条链路");
        exchangeData.setReplyContent("先启动，再编排，再执行");
        exchangeData.setSourceSnapshotList("[\"来源：执行计划\"]");
        exchangeData.setFollowupSuggestionList("[\"继续说明执行计划\"]");
        exchangeData.setToolTraceList("[\"执行模式：开放式提问\"]");
        exchangeData.setExchangeState(2);
        exchangeData.setFirstTokenLatencyMs(120L);
        exchangeData.setTotalLatencyMs(800L);
        exchangeData.setCreateTime(LocalDateTime.of(2026, 4, 23, 11, 35, 0));

        BusinessChatMemorySummaryData summaryData = new BusinessChatMemorySummaryData();
        summaryData.setSummaryText("最近围绕链路执行进行问答");
        summaryData.setSummaryJson("{\"latestQuestion\":\"请说明这条链路\"}");

        when(businessChatDialogueMapper.selectOne(any())).thenReturn(dialogueData);
        when(businessChatMemorySummaryMapper.selectOne(any())).thenReturn(summaryData);
        when(businessChatExchangeMapper.selectList(any())).thenReturn(List.of(exchangeData));

        var detailVo = businessChatQueryService.getSession(request);

        assertThat(detailVo.getConversationId()).isEqualTo("conversation-1");
        assertThat(detailVo.getTitle()).isEqualTo("链路说明");
        assertThat(detailVo.getChatMode()).isEqualTo("OPEN_ENDED");
        assertThat(detailVo.getDialogueStage()).isEqualTo("IDLE");
        assertThat(detailVo.getSummaryText()).isEqualTo("最近围绕链路执行进行问答");
        assertThat(detailVo.getSummaryJson()).isEqualTo(Map.of("latestQuestion", "请说明这条链路"));
        assertThat(detailVo.getExchanges()).hasSize(1);
        assertThat(detailVo.getExchanges().getFirst().getExchangeState()).isEqualTo("COMPLETED");
        assertThat(detailVo.getExchanges().getFirst().getSourceSnapshotList())
                .containsExactly("来源：执行计划");
        assertThat(detailVo.getExchanges().getFirst().getFollowUpSuggestionList())
                .containsExactly("继续说明执行计划");
        assertThat(detailVo.getExchanges().getFirst().getToolTraceList())
                .containsExactly("执行模式：开放式提问");
    }

    @Test
    void shouldReturnBlankTitleWhenTitleHasNotBeenGenerated() {
        BusinessChatSessionDetailRequest request = new BusinessChatSessionDetailRequest();
        request.setConversationId("conversation-running");

        BusinessChatDialogueData dialogueData = new BusinessChatDialogueData();
        dialogueData.setDialogueCode("conversation-running");
        dialogueData.setDialogueTitle("");
        dialogueData.setDialogueStage(2);
        dialogueData.setChatMode(3);

        when(businessChatDialogueMapper.selectOne(any())).thenReturn(dialogueData);
        when(businessChatMemorySummaryMapper.selectOne(any())).thenReturn(null);
        when(businessChatExchangeMapper.selectList(any())).thenReturn(List.of());

        var detailVo = businessChatQueryService.getSession(request);

        assertThat(detailVo.getConversationId()).isEqualTo("conversation-running");
        assertThat(detailVo.getTitle()).isEmpty();
        assertThat(detailVo.getDialogueStage()).isEqualTo("RUNNING");
        assertThat(detailVo.getExchanges()).isEmpty();
    }

    @Test
    void shouldThrowWhenSessionDoesNotExist() {
        BusinessChatSessionDetailRequest request = new BusinessChatSessionDetailRequest();
        request.setConversationId("conversation-missing");

        when(businessChatDialogueMapper.selectOne(any())).thenReturn(null);

        assertThatThrownBy(() -> businessChatQueryService.getSession(request))
                .isInstanceOf(BaseException.class)
                .extracting("code")
                .isEqualTo(BusinessChatErrorCode.CHAT_SESSION_NOT_FOUND.getCode());
    }
}
