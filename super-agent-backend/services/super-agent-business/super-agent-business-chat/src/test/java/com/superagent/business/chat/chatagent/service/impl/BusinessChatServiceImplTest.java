package com.superagent.business.chat.chatagent.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.superagent.business.chat.chatagent.agent.BusinessChatAgent;
import com.superagent.business.chat.chatagent.agent.BusinessChatAgentRegistry;
import com.superagent.business.chat.chatagent.agent.BusinessChatAgentType;
import com.superagent.business.chat.chatagent.dto.BusinessChatStreamRequest;
import com.superagent.business.chat.chatagent.finalization.BusinessChatFinalizationGenerator;
import com.superagent.business.chat.chatagent.finalization.BusinessChatFinalizationResult;
import com.superagent.business.chat.chatagent.model.BusinessChatExecutionPlan;
import com.superagent.business.chat.chatagent.runtime.BusinessChatFinalizedTurn;
import com.superagent.business.chat.chatagent.model.BusinessChatFreshnessRequirement;
import com.superagent.business.chat.chatagent.model.BusinessChatMode;
import com.superagent.business.chat.chatagent.model.BusinessChatModelApiConfigSnapshot;
import com.superagent.business.chat.chatagent.model.BusinessChatModelProvider;
import com.superagent.business.chat.chatagent.runtime.BusinessChatRuntimeContext;
import com.superagent.business.chat.chatagent.model.BusinessChatTaskInfo;
import com.superagent.business.chat.chatagent.orchestration.BusinessChatOrchestrator;
import com.superagent.business.chat.chatagent.persistence.BusinessChatPersistenceService;
import com.superagent.business.chat.chatagent.service.BusinessChatModelApiConfigService;
import com.superagent.business.chat.chatagent.runtime.BusinessChatRuntimeRegistry;
import com.superagent.business.chat.chatagent.vo.BusinessChatStreamEvent;
import com.superagent.business.chat.knowledge.service.KnowledgeManageService;
import com.superagent.redisson.servicelease.lease.RedisLeaseManager;
import java.time.Duration;
import java.util.List;
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
            "api-key");

    private final BusinessChatModelApiConfigSnapshot secondModelConfig = new BusinessChatModelApiConfigSnapshot(
            3002L,
            BusinessChatModelProvider.DEEPSEEK,
            "DeepSeek",
            "https://api.deepseek.com",
            "deepseek-v4-pro",
            "api-key");

    @BeforeEach
    void setUp() {
        businessChatService = new BusinessChatServiceImpl(
                redisLeaseManager,
                businessChatPersistenceService,
                businessChatRuntimeRegistry,
                businessChatOrchestrator,
                businessChatAgentRegistry,
                businessChatFinalizationGenerator,
                modelApiConfigService,
                knowledgeManageService);
        lenient().when(modelApiConfigService.getRequiredAvailableSnapshot("3001")).thenReturn(modelConfig);
        lenient().when(modelApiConfigService.getRequiredAvailableSnapshot("3002")).thenReturn(secondModelConfig);
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
    void shouldUseCurrentRequestModelWhenSameConversationSwitchesModel() {
        when(redisLeaseManager.acquire(any(), any(), any())).thenReturn(true);
        when(redisLeaseManager.release(any(), any())).thenReturn(true);
        when(businessChatPersistenceService.createTurnRecordAndBuildTaskInfo(any()))
                .thenAnswer(invocation -> {
                    var startPlan = invocation.getArgument(0, com.superagent.business.chat.chatagent.model.BusinessChatStartPlan.class);
                    return new BusinessChatTaskInfo(
                            1001L,
                            startPlan.modelConfig().id() + 10000L,
                            startPlan.question(),
                            startPlan.conversationId(),
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
                            0,
                            null,
                            new BusinessChatFreshnessRequirement(false, "用户问题未命中明确实时信息信号", List.of(), "NOT_REQUIRED"),
                            "NOT_REQUIRED",
                            List.of(),
                            runtimeContext.getTaskInfo().modelConfig().modelName(),
                            "open_ended_question_answer",
                            "根据会话模式、历史上下文、知识路由和时效性要求生成本轮执行计划。",
                            BusinessChatAgentType.THINK_ACT,
                            BusinessChatMode.OPEN_ENDED,
                            List.of("执行模型：" + runtimeContext.getTaskInfo().modelConfig().modelName()));
                });
        when(businessChatAgentRegistry.getRequiredAgent(BusinessChatAgentType.THINK_ACT))
                .thenReturn(businessChatAgent);
        when(businessChatAgent.execute(any(), any())).thenReturn(Flux.just("ok"));
        when(businessChatPersistenceService.dialogueTitleExists(any())).thenReturn(false);
        when(businessChatFinalizationGenerator.generate(any(), anyBoolean())).thenReturn(
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
        BusinessChatTaskInfo taskInfo = new BusinessChatTaskInfo(
                1001L,
                2001L,
                "请帮我说明这条链路",
                "conversation-1",
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
        BusinessChatExecutionPlan executionPlan = new BusinessChatExecutionPlan(
                "请帮我说明这条链路",
                "请帮我说明这条链路",
                null,
                null,
                0,
                null,
                new BusinessChatFreshnessRequirement(false, "用户问题未命中明确实时信息信号", List.of(), "NOT_REQUIRED"),
                "NOT_REQUIRED",
                List.of(),
                "CHAT_CLIENT_DEFAULT",
                "open_ended_question_answer",
                "根据会话模式、历史上下文、知识路由和时效性要求生成本轮执行计划。",
                BusinessChatAgentType.THINK_ACT,
                BusinessChatMode.OPEN_ENDED,
                List.of(
                        "加载长期摘要：无",
                        "加载最近对话窗口：0轮",
                        "问题改写：请帮我说明这条链路",
                        "时效性判断：不需要实时信息",
                        "知识路由：NOT_REQUIRED",
                        "执行模型：CHAT_CLIENT_DEFAULT",
                        "按执行计划生成流式正文",
                        "补发执行补充信息与推荐追问"));

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
        lenient().when(businessChatFinalizationGenerator.generate(any(), anyBoolean())).thenReturn(
                new BusinessChatFinalizationResult(
                        "链路说明",
                        List.of("如何继续拆解链路？", "有哪些关键风险？", "如何落地执行？")));
        return runtimeContext;
    }

    private BusinessChatStreamRequest createRequest() {
        BusinessChatStreamRequest request = new BusinessChatStreamRequest();
        request.setQuestion("请帮我说明这条链路");
        request.setConversationId("conversation-1");
        request.setChatMode("OPEN_ENDED");
        request.setModelConfigId("3001");
        return request;
    }
}
