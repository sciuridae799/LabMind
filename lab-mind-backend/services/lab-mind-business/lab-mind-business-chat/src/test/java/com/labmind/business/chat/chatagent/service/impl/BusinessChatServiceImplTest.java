package com.labmind.business.chat.chatagent.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.labmind.business.chat.chatagent.execution.agent.BusinessChatAgent;
import com.labmind.business.chat.chatagent.execution.agent.BusinessChatAgentRegistry;
import com.labmind.business.chat.chatagent.execution.agent.BusinessChatAgentType;
import com.labmind.business.chat.chatagent.api.dto.BusinessChatStreamRequest;
import com.labmind.business.chat.chatagent.orchestration.finalization.BusinessChatFinalizationGenerator;
import com.labmind.business.chat.chatagent.orchestration.finalization.BusinessChatFinalizationResult;
import com.labmind.business.chat.chatagent.orchestration.model.BusinessChatAgentStep;
import com.labmind.business.chat.chatagent.orchestration.model.BusinessChatClarificationPlan;
import com.labmind.business.chat.chatagent.orchestration.model.BusinessChatExecutionPlan;
import com.labmind.business.chat.chatagent.runtime.BusinessChatFinalizedTurn;
import com.labmind.business.chat.chatagent.orchestration.model.BusinessChatFreshnessRequirement;
import com.labmind.business.chat.chatagent.orchestration.model.BusinessChatMode;
import com.labmind.business.chat.chatagent.execution.model.BusinessChatModelApiConfigSnapshot;
import com.labmind.business.chat.chatagent.execution.model.BusinessChatModelProvider;
import com.labmind.business.chat.chatagent.logging.BusinessChatBusinessFlowLogger;
import com.labmind.business.chat.chatagent.runtime.BusinessChatRuntimeContext;
import com.labmind.business.chat.chatagent.runtime.BusinessChatTaskInfo;
import com.labmind.business.chat.chatagent.orchestration.BusinessChatOrchestrator;
import com.labmind.business.chat.chatagent.persistence.BusinessChatPersistenceService;
import com.labmind.business.chat.chatagent.service.BusinessChatModelApiConfigService;
import com.labmind.business.chat.chatagent.runtime.BusinessChatRuntimeRegistry;
import com.labmind.business.chat.chatagent.trace.BusinessChatTraceStageRunner;
import com.labmind.business.chat.chatagent.api.vo.BusinessChatStreamEvent;
import com.labmind.business.chat.auth.AuthRole;
import com.labmind.business.chat.auth.AuthSessionContext;
import com.labmind.business.chat.auth.AuthSessionHolder;
import com.labmind.business.chat.knowledge.retrieval.KnowledgeRetrievalParentEvidence;
import com.labmind.business.chat.knowledge.document.service.KnowledgeManageService;
import com.labmind.redisson.servicelease.lease.RedisLeaseManager;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

@ExtendWith(MockitoExtension.class)
class BusinessChatServiceImplTest {

    @Mock
    private RedisLeaseManager redisLeaseManager;

    @Mock
    private BusinessChatPersistenceService businessChatPersistenceService;

    @Mock
    private BusinessChatRuntimeRegistry businessChatRuntimeRegistry;

    @Mock
    private BusinessChatOrchestrator businessChatOrchestrator;

    @Mock
    private BusinessChatAgentRegistry businessChatAgentRegistry;

    @Mock
    private BusinessChatAgent businessChatAgent;

    @Mock
    private BusinessChatFinalizationGenerator businessChatFinalizationGenerator;

    @Mock
    private BusinessChatModelApiConfigService modelApiConfigService;

    @Mock
    private KnowledgeManageService knowledgeManageService;

    private BusinessChatServiceImpl businessChatService;

    private final BusinessChatModelApiConfigSnapshot modelConfig = new BusinessChatModelApiConfigSnapshot(
            3001L,
            BusinessChatModelProvider.DASHSCOPE,
            "DASHSCOPE",
            "https://dashscope.aliyuncs.com/compatible-mode",
            "qwen-plus",
            "api-key", java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, 1000, "CNY");

    private final BusinessChatModelApiConfigSnapshot secondModelConfig = new BusinessChatModelApiConfigSnapshot(
            3002L,
            BusinessChatModelProvider.DEEPSEEK,
            "DeepSeek",
            "https://api.deepseek.com",
            "deepseek-v4-pro",
            "api-key", java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, 1000, "CNY");

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
        BusinessChatTraceStageRunner traceStageRunner = new BusinessChatTraceStageRunner(businessChatPersistenceService);
        businessChatService = new BusinessChatServiceImpl(
                redisLeaseManager,
                businessChatPersistenceService,
                businessChatRuntimeRegistry,
                businessChatOrchestrator,
                businessChatAgentRegistry,
                businessChatFinalizationGenerator,
                modelApiConfigService,
                knowledgeManageService,
                traceStageRunner,
                new BusinessChatBusinessFlowLogger());
        lenient().when(modelApiConfigService.getRequiredAvailableSnapshot("3001")).thenReturn(modelConfig);
        lenient().when(modelApiConfigService.getRequiredAvailableSnapshot("3002")).thenReturn(secondModelConfig);
    }

    @AfterEach
    void tearDown() {
        AuthSessionHolder.clear();
    }

    @Test
    void shouldReturnRejectedStreamWhenConversationLeaseCannotBeAcquired() {
        when(redisLeaseManager.acquire(any(), any(), any())).thenReturn(false);

        List<ServerSentEvent<BusinessChatStreamEvent>> events = businessChatService.streamChat(createRequest())
                .collectList()
                .block();

        assertThat(events).hasSize(1);
        assertThat(events.getFirst().data()).extracting(BusinessChatStreamEvent::eventType).isEqualTo("TURN_REJECTED");
        verify(businessChatPersistenceService, never()).createTurnRecordAndBuildTaskInfo(any());
    }

    @Test
    void shouldGenerateConversationIdWhenStreamRequestDoesNotProvideOne() {
        when(redisLeaseManager.acquire(any(), any(), any())).thenReturn(false);
        BusinessChatStreamRequest request = createRequest();
        request.setConversationId(" ");

        List<ServerSentEvent<BusinessChatStreamEvent>> events = businessChatService.streamChat(request)
                .collectList()
                .block();

        BusinessChatStreamEvent event = events.getFirst().data();
        assertThat(event.conversationId()).isNotBlank().hasSize(32).doesNotContain("-");
        ArgumentCaptor<String> leaseKeyCaptor = ArgumentCaptor.forClass(String.class);
        verify(redisLeaseManager).acquire(leaseKeyCaptor.capture(), any(), any());
        assertThat(leaseKeyCaptor.getValue()).isEqualTo("chat:conversation:running:" + event.conversationId());
        verify(businessChatPersistenceService, never()).createTurnRecordAndBuildTaskInfo(any());
    }

    @Test
    void shouldStreamTextSupplementFollowupsAndFinishEvents() {
        BusinessChatRuntimeContext runtimeContext = prepareSuccessfulRuntime(
                Flux.just("第一段", "第二段"));

        List<ServerSentEvent<BusinessChatStreamEvent>> events = businessChatService.streamChat(createRequest())
                .collectList()
                .block(Duration.ofSeconds(5));

        assertThat(events)
                .extracting(event -> event.data().eventType())
                .containsExactly(
                        "EXECUTION_PROGRESS",
                        "AGENT_STARTED",
                        "TEXT_DELTA",
                        "TEXT_DELTA",
                        "AGENT_FINISHED",
                        "FUNCTION_SUPPLEMENT",
                        "REFERENCE_SUPPLEMENT",
                        "FOLLOW_UP_RECOMMENDATION",
                        "TURN_FINISHED");
        assertThat(runtimeContext.getReplyContent()).isEqualTo("第一段第二段");
        ArgumentCaptor<BusinessChatFinalizedTurn> finalizedTurnCaptor =
                ArgumentCaptor.forClass(BusinessChatFinalizedTurn.class);
        verify(businessChatPersistenceService).archiveSucceededTurn(finalizedTurnCaptor.capture());
        assertThat(finalizedTurnCaptor.getValue().replyContent()).isEqualTo("第一段第二段");
        assertThat(finalizedTurnCaptor.getValue().followUpSuggestionList()).hasSize(3);
        verify(businessChatPersistenceService).updateDialogueTitleIfAbsent(any(), any());
        verify(businessChatPersistenceService, timeout(1000)).refreshConversationSummary(any());
        verify(redisLeaseManager).release(any(), any());
    }

    @Test
    void shouldKeepSucceededTerminalStateWhenSummaryRefreshFails() {
        BusinessChatRuntimeContext runtimeContext = prepareSuccessfulRuntime(
                Flux.just("第一段", "第二段"));
        doThrow(new IllegalStateException("summary refresh failed"))
                .when(businessChatPersistenceService)
                .refreshConversationSummary(any());

        List<ServerSentEvent<BusinessChatStreamEvent>> events = businessChatService.streamChat(createRequest())
                .collectList()
                .block(Duration.ofSeconds(5));

        assertThat(events)
                .extracting(event -> event.data().eventType())
                .containsExactly(
                        "EXECUTION_PROGRESS",
                        "AGENT_STARTED",
                        "TEXT_DELTA",
                        "TEXT_DELTA",
                        "AGENT_FINISHED",
                        "FUNCTION_SUPPLEMENT",
                        "REFERENCE_SUPPLEMENT",
                        "FOLLOW_UP_RECOMMENDATION",
                        "TURN_FINISHED");
        verify(businessChatPersistenceService).archiveSucceededTurn(any());
        verify(businessChatPersistenceService, never()).archiveFailedTurn(any(), any());
        verify(businessChatPersistenceService).refreshConversationSummary(any());
        verify(redisLeaseManager).release(any(), any());
        assertThat(runtimeContext.getReplyContent()).isEqualTo("第一段第二段");
    }

    @Test
    void shouldArchiveFailureWhenRecommendationFinalizationFailsAfterAnswer() {
        BusinessChatRuntimeContext runtimeContext = prepareSuccessfulRuntime(
                Flux.just("第一段", "第二段"));
        when(businessChatFinalizationGenerator.generate(any(), any(), anyBoolean()))
                .thenThrow(new IllegalStateException("recommendation failed"));

        List<ServerSentEvent<BusinessChatStreamEvent>> events = businessChatService.streamChat(createRequest())
                .collectList()
                .block(Duration.ofSeconds(5));

        assertThat(events)
                .extracting(event -> event.data().eventType())
                .containsExactly(
                        "EXECUTION_PROGRESS",
                        "AGENT_STARTED",
                        "TEXT_DELTA",
                        "TEXT_DELTA",
                        "AGENT_FINISHED",
                        "FUNCTION_SUPPLEMENT",
                        "TURN_FAILED");
        assertThat(runtimeContext.getReplyContent()).isEqualTo("第一段第二段");
        verify(businessChatPersistenceService, never()).archiveSucceededTurn(any());
        verify(businessChatPersistenceService).archiveFailedTurn(runtimeContext, "recommendation failed");
        verify(businessChatPersistenceService, never()).updateDialogueTitleIfAbsent(any(), any());
        verify(redisLeaseManager).release(any(), any());
    }

    @Test
    void shouldArchiveRetrievalEvidenceAsSourceSnapshots() {
        BusinessChatRuntimeContext runtimeContext = prepareSuccessfulRuntime(
                Flux.just("基于证据回答 [1]"),
                buildExecutionPlan(List.of(new KnowledgeRetrievalParentEvidence(
                        8001L,
                        9001L,
                        "订单审核手册",
                        "第一章",
                        "订单审核包括提交、风控、人工审核和归档。",
                        1.0D,
                        List.of(7001L),
                        List.of("VECTOR")))));

        List<ServerSentEvent<BusinessChatStreamEvent>> events = businessChatService.streamChat(createRequest())
                .collectList()
                .block(Duration.ofSeconds(5));

        BusinessChatStreamEvent referenceEvent = events.stream()
                .map(ServerSentEvent::data)
                .filter(event -> "REFERENCE_SUPPLEMENT".equals(event.eventType()))
                .findFirst()
                .orElseThrow();
        String sourceSnapshot = """
                [1]
                文档名称：订单审核手册
                章节路径：第一章
                引用内容：
                订单审核包括提交、风控、人工审核和归档。""";
        assertThat(referenceEvent.sourceSnapshotList())
                .containsExactly(sourceSnapshot);
        ArgumentCaptor<BusinessChatFinalizedTurn> finalizedTurnCaptor =
                ArgumentCaptor.forClass(BusinessChatFinalizedTurn.class);
        verify(businessChatPersistenceService).archiveSucceededTurn(finalizedTurnCaptor.capture());
        assertThat(finalizedTurnCaptor.getValue().sourceSnapshotList())
                .containsExactly(sourceSnapshot);
        assertThat(runtimeContext.getSourceSnapshotList())
                .containsExactly(sourceSnapshot);
    }

    @Test
    void shouldArchiveFailureAndEmitFailedEventWhenExecutorThrows() {
        BusinessChatRuntimeContext runtimeContext = prepareSuccessfulRuntime(
                Flux.error(new IllegalStateException("executor failed")));

        List<ServerSentEvent<BusinessChatStreamEvent>> events = businessChatService.streamChat(createRequest())
                .collectList()
                .block(Duration.ofSeconds(5));

        assertThat(events)
                .extracting(event -> event.data().eventType())
                .containsExactly("EXECUTION_PROGRESS", "AGENT_STARTED", "TURN_FAILED");
        verify(businessChatPersistenceService).archiveFailedTurn(runtimeContext, "executor failed");
        verify(businessChatPersistenceService, never()).refreshConversationSummary(any());
    }

    @Test
    void shouldArchiveStoppedTurnWhenStreamIsCancelled() {
        BusinessChatRuntimeContext runtimeContext = prepareSuccessfulRuntime(Flux.never());

        List<ServerSentEvent<BusinessChatStreamEvent>> events = businessChatService.streamChat(createRequest())
                .take(1)
                .collectList()
                .block(Duration.ofSeconds(5));

        assertThat(events)
                .extracting(event -> event.data().eventType())
                .containsExactly("EXECUTION_PROGRESS");
        verify(businessChatPersistenceService, timeout(1000))
                .archiveStoppedTurn(runtimeContext, "本轮回答已中止");
        verify(businessChatPersistenceService, never()).archiveSucceededTurn(any());
        verify(businessChatPersistenceService, never()).refreshConversationSummary(any());
    }

    @Test
    void shouldReleaseRuntimeResourcesWhenStoppedTurnArchiveFails() {
        BusinessChatRuntimeContext runtimeContext = prepareSuccessfulRuntime(Flux.never());
        doThrow(new IllegalStateException("archive failed"))
                .when(businessChatPersistenceService)
                .archiveStoppedTurn(any(), any());

        businessChatService.streamChat(createRequest())
                .take(1)
                .collectList()
                .block(Duration.ofSeconds(5));

        verify(businessChatRuntimeRegistry, timeout(1000)).unregister("conversation-1");
        verify(redisLeaseManager, timeout(1000))
                .release("chat:conversation:running:conversation-1", "owner-1");
    }

    @Test
    void shouldUseCurrentRequestModelWhenSameConversationSwitchesModel() {
        when(redisLeaseManager.acquire(any(), any(), any())).thenReturn(true);
        when(redisLeaseManager.release(any(), any())).thenReturn(true);
        when(businessChatPersistenceService.createTurnRecordAndBuildTaskInfo(any()))
                .thenAnswer(invocation -> {
                    var startPlan = invocation.getArgument(0, com.labmind.business.chat.chatagent.orchestration.model.BusinessChatStartPlan.class);
                    return new BusinessChatTaskInfo(
                            1001L,
                            startPlan.modelConfig().id() + 10000L,
                            startPlan.question(),
                            startPlan.conversationId(),
                            startPlan.workspaceId(),
                            startPlan.authSessionToken(),
                            startPlan.chatMode(),
                            startPlan.modelConfig(),
                            startPlan.selectedDocumentId(),
                            startPlan.selectedDocumentName(),
                            startPlan.traceId(),
                            startPlan.leaseKey(),
                            startPlan.leaseOwnerToken(),
                            startPlan.leaseTtl(),
                            startPlan.startAtEpochMillis());
                });
        when(businessChatRuntimeRegistry.register(any()))
                .thenAnswer(invocation -> {
                    BusinessChatTaskInfo taskInfo = invocation.getArgument(0, BusinessChatTaskInfo.class);
                    return new BusinessChatRuntimeContext(taskInfo, Sinks.many().unicast().onBackpressureBuffer());
                });
        doNothing().when(businessChatRuntimeRegistry).unregister("conversation-1");
        when(businessChatOrchestrator.orchestrate(any()))
                .thenAnswer(invocation -> {
                    BusinessChatRuntimeContext runtimeContext = invocation.getArgument(0, BusinessChatRuntimeContext.class);
                    return new BusinessChatExecutionPlan(
                            runtimeContext.getTaskInfo().question(),
                            runtimeContext.getTaskInfo().question(),
                            null,
                            null,
                            null,
                            0,
                            null,
                            null,
                            List.of(),
                            new BusinessChatFreshnessRequirement(false, "用户问题未命中明确实时信息信号", List.of(), "NOT_REQUIRED"),
                            "NOT_REQUIRED",
                            List.of(),
                            runtimeContext.getTaskInfo().modelConfig().modelName(),
                            "open_ended_question_answer",
                            "根据会话模式、历史上下文、知识路由和时效性要求生成本轮执行计划。",
                            List.of(agentStep(BusinessChatAgentType.THINK_ACT)),
                            BusinessChatMode.OPEN_ENDED,
                            BusinessChatClarificationPlan.notRequired(),
                            false,
                null,
                List.of("执行模型：" + runtimeContext.getTaskInfo().modelConfig().modelName()));
                });
        when(businessChatAgentRegistry.getRequiredAgent(BusinessChatAgentType.THINK_ACT))
                .thenReturn(businessChatAgent);
        when(businessChatAgent.execute(any(), any())).thenReturn(Flux.just("ok"));
        when(businessChatPersistenceService.dialogueTitleExists(any())).thenReturn(false);
        when(businessChatFinalizationGenerator.generate(any(), any(), anyBoolean())).thenReturn(
                new BusinessChatFinalizationResult("模型验证", List.of("继续验证？", "查看追踪？", "检查归档？")));

        businessChatService.streamChat(createRequest()).collectList().block(Duration.ofSeconds(5));
        BusinessChatStreamRequest secondRequest = createRequest();
        secondRequest.setModelConfigId("3002");
        businessChatService.streamChat(secondRequest).collectList().block(Duration.ofSeconds(5));

        ArgumentCaptor<BusinessChatRuntimeContext> runtimeContextCaptor =
                ArgumentCaptor.forClass(BusinessChatRuntimeContext.class);
        verify(businessChatAgent, org.mockito.Mockito.times(2)).execute(runtimeContextCaptor.capture(), any());
        assertThat(runtimeContextCaptor.getAllValues())
                .extracting(context -> context.getTaskInfo().modelConfig().modelName())
                .containsExactly("qwen-plus", "deepseek-v4-pro");
    }

    private BusinessChatRuntimeContext prepareSuccessfulRuntime(Flux<String> executionFlux) {
        return prepareSuccessfulRuntime(executionFlux, buildExecutionPlan(List.of()));
    }

    private BusinessChatRuntimeContext prepareSuccessfulRuntime(
            Flux<String> executionFlux,
            BusinessChatExecutionPlan executionPlan) {
        BusinessChatTaskInfo taskInfo = new BusinessChatTaskInfo(
                1001L,
                2001L,
                "请帮我说明这条链路",
                "conversation-1",
                "workspace-1",
                "",
                BusinessChatMode.OPEN_ENDED,
                modelConfig,
                null,
                null,
                "trace-1",
                "chat:conversation:running:conversation-1",
                "owner-1",
                Duration.ofSeconds(30),
                System.currentTimeMillis());
        BusinessChatRuntimeContext runtimeContext =
                new BusinessChatRuntimeContext(taskInfo, Sinks.many().unicast().onBackpressureBuffer());
        when(redisLeaseManager.acquire(any(), any(), any())).thenReturn(true);
        when(redisLeaseManager.release(any(), any())).thenReturn(true);
        when(businessChatPersistenceService.createTurnRecordAndBuildTaskInfo(any())).thenReturn(taskInfo);
        when(businessChatRuntimeRegistry.register(taskInfo)).thenReturn(runtimeContext);
        doNothing().when(businessChatRuntimeRegistry).unregister(taskInfo.conversationId());
        lenient().when(businessChatOrchestrator.orchestrate(runtimeContext)).thenReturn(executionPlan);
        lenient().when(businessChatAgentRegistry.getRequiredAgent(BusinessChatAgentType.THINK_ACT))
                .thenReturn(businessChatAgent);
        lenient().when(businessChatAgent.execute(runtimeContext, executionPlan)).thenReturn(executionFlux);
        lenient().when(businessChatPersistenceService.dialogueTitleExists(any())).thenReturn(false);
        lenient().when(businessChatFinalizationGenerator.generate(any(), any(), anyBoolean())).thenReturn(
                new BusinessChatFinalizationResult(
                        "链路说明",
                        List.of("如何继续拆解链路？", "有哪些关键风险？", "如何落地执行？")));
        return runtimeContext;
    }

    private BusinessChatExecutionPlan buildExecutionPlan(List<KnowledgeRetrievalParentEvidence> evidenceList) {
        return new BusinessChatExecutionPlan(
                "请帮我说明这条链路",
                "请帮我说明这条链路",
                null,
                null,
                null,
                0,
                null,
                evidenceList.isEmpty() ? null : "[1]\nParent正文：\n订单审核包括提交、风控、人工审核和归档。",
                evidenceList,
                new BusinessChatFreshnessRequirement(false, "用户问题未命中明确实时信息信号", List.of(), "NOT_REQUIRED"),
                "NOT_REQUIRED",
                List.of(),
                "CHAT_CLIENT_DEFAULT",
                "open_ended_question_answer",
                "根据会话模式、历史上下文、知识路由和时效性要求生成本轮执行计划。",
                List.of(agentStep(BusinessChatAgentType.THINK_ACT)),
                BusinessChatMode.OPEN_ENDED,
                BusinessChatClarificationPlan.notRequired(),
                false,
                null,
                List.of(
                        "加载长期摘要：无",
                        "加载最近对话窗口：0轮",
                        "问题改写使用历史：否",
                        "问题改写：请帮我说明这条链路",
                        "时效性判断：不需要实时信息",
                        "知识路由：NOT_REQUIRED",
                        "执行模型：CHAT_CLIENT_DEFAULT",
                        "按执行计划生成流式正文",
                        "补发执行补充信息与推荐追问"));
    }

    private BusinessChatAgentStep agentStep(BusinessChatAgentType agentType) {
        return new BusinessChatAgentStep(
                agentType,
                "AGENT_" + agentType.getValue(),
                agentType.getDisplayName(),
                710,
                true);
    }

    private BusinessChatStreamRequest createRequest() {
        BusinessChatStreamRequest request = new BusinessChatStreamRequest();
        request.setQuestion("请帮我说明这条链路");
        request.setConversationId("conversation-1");
        request.setWorkspaceId("workspace-1");
        request.setChatMode("OPEN_ENDED");
        request.setModelConfigId("3001");
        return request;
    }
}
