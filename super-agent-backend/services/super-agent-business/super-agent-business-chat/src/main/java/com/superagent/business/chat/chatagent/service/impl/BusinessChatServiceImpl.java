package com.superagent.business.chat.chatagent.service.impl;

import com.superagent.business.chat.chatagent.agent.BusinessChatAgentEvent;
import com.superagent.business.chat.chatagent.agent.BusinessChatAgentRegistry;
import com.superagent.business.chat.chatagent.dto.BusinessChatStreamRequest;
import com.superagent.business.chat.chatagent.finalization.BusinessChatFinalizationGenerator;
import com.superagent.business.chat.chatagent.finalization.BusinessChatFinalizationResult;
import com.superagent.business.chat.chatagent.model.BusinessChatConversationLeaseKeys;
import com.superagent.business.chat.chatagent.model.BusinessChatEventType;
import com.superagent.business.chat.chatagent.model.BusinessChatExecutionPlan;
import com.superagent.business.chat.chatagent.model.BusinessChatIntentAnalysis;
import com.superagent.business.chat.chatagent.model.BusinessChatMode;
import com.superagent.business.chat.chatagent.model.BusinessChatModelApiConfigSnapshot;
import com.superagent.business.chat.chatagent.model.BusinessChatStartPlan;
import com.superagent.business.chat.chatagent.model.BusinessChatTaskInfo;
import com.superagent.business.chat.chatagent.orchestration.BusinessChatOrchestrator;
import com.superagent.business.chat.chatagent.persistence.BusinessChatPersistenceService;
import com.superagent.business.chat.chatagent.runtime.BusinessChatFinalizedTurn;
import com.superagent.business.chat.chatagent.runtime.BusinessChatRuntimeContext;
import com.superagent.business.chat.chatagent.runtime.BusinessChatRuntimeRegistry;
import com.superagent.business.chat.chatagent.service.BusinessChatErrorCode;
import com.superagent.business.chat.chatagent.service.BusinessChatModelApiConfigService;
import com.superagent.business.chat.chatagent.service.BusinessChatService;
import com.superagent.business.chat.chatagent.vo.BusinessChatStreamEvent;
import com.superagent.common.frame.exception.BaseException;
import com.superagent.redisson.servicelease.lease.RedisLeaseManager;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

@Service
@Slf4j
@RequiredArgsConstructor
public class BusinessChatServiceImpl implements BusinessChatService {

    private static final Duration CONVERSATION_LEASE_TTL = Duration.ofSeconds(30);

    private static final Duration LEASE_RENEW_INTERVAL = Duration.ofSeconds(10);

    private static final String FINALIZE_STAGE_CODE = "FINALIZE";

    private static final String RECOMMENDATION_STAGE_CODE = "RECOMMENDATION";

    private final RedisLeaseManager redisLeaseManager;

    private final BusinessChatPersistenceService businessChatPersistenceService;

    private final BusinessChatRuntimeRegistry businessChatRuntimeRegistry;

    private final BusinessChatOrchestrator businessChatOrchestrator;

    private final BusinessChatAgentRegistry businessChatAgentRegistry;

    private final BusinessChatFinalizationGenerator businessChatFinalizationGenerator;

    private final BusinessChatModelApiConfigService modelApiConfigService;

    @Override
    public Flux<ServerSentEvent<BusinessChatStreamEvent>> streamChat(BusinessChatStreamRequest request) {
        return Flux.defer(() -> startDeferredChatStream(request));
    }

    private Flux<ServerSentEvent<BusinessChatStreamEvent>> startDeferredChatStream(BusinessChatStreamRequest request) {
        // 输入流：请求参数 -> StartPlan。
        BusinessChatStartPlan startPlan = normalizeRequestAndBuildStartPlan(request);
        if (!tryAcquireConversationLease(startPlan)) {
            return buildRejectedStream(startPlan, "该会话当前正在执行中");
        }
        BusinessChatTaskInfo taskInfo = null;
        BusinessChatRuntimeContext runtimeContext = null;
        try {
            // 运行流：StartPlan -> RUNNING exchange -> RuntimeContext。
            taskInfo = createTurnRecordAndBuildTaskInfo(startPlan);
            runtimeContext = registerRuntimeWorkbench(taskInfo);
            BusinessChatRuntimeContext boundRuntimeContext = runtimeContext;
            Flux<ServerSentEvent<BusinessChatStreamEvent>> outputFlux = bindSseOutputChannel(boundRuntimeContext);
            // 输出流：RuntimeContext.outputChannel -> SSE。
            Flux<ServerSentEvent<BusinessChatStreamEvent>> executionFlux = Mono.delay(Duration.ZERO)
                    .then(startLeaseRenewalAndExecution(boundRuntimeContext))
                    .onErrorResume(error -> handleExecutionFailure(boundRuntimeContext, error))
                    .doFinally(signalType -> {
                        handleExecutionCancellation(boundRuntimeContext, signalType);
                        releaseRuntimeResources(boundRuntimeContext);
                    })
                    .thenMany(Flux.empty());
            return outputFlux.mergeWith(executionFlux);
        } catch (Throwable error) {
            handleStartFailure(startPlan, taskInfo, runtimeContext, error);
            throw error;
        }
    }

    private BusinessChatStartPlan normalizeRequestAndBuildStartPlan(BusinessChatStreamRequest request) {
        String question = normalizeRequiredText(request.getQuestion(), "question");
        String conversationId = normalizeOptionalConversationId(request.getConversationId());
        BusinessChatMode chatMode = BusinessChatMode.fromValue(request.getChatMode());
        BusinessChatModelApiConfigSnapshot modelConfig =
                modelApiConfigService.getRequiredAvailableSnapshot(request.getModelConfigId());
        long startAtEpochMillis = System.currentTimeMillis();
        return new BusinessChatStartPlan(
                question,
                conversationId,
                chatMode,
                modelConfig,
                UUID.randomUUID().toString().replace("-", ""),
                BusinessChatConversationLeaseKeys.conversationLeaseKey(conversationId),
                UUID.randomUUID().toString(),
                CONVERSATION_LEASE_TTL,
                startAtEpochMillis);
    }

    private String normalizeOptionalConversationId(String conversationId) {
        String normalizedConversationId = conversationId == null ? null : conversationId.strip();
        return StringUtils.hasText(normalizedConversationId)
                ? normalizedConversationId
                : generateConversationId();
    }

    private String generateConversationId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private boolean tryAcquireConversationLease(BusinessChatStartPlan startPlan) {
        return redisLeaseManager.acquire(startPlan.leaseKey(), startPlan.leaseOwnerToken(), startPlan.leaseTtl());
    }

    private BusinessChatTaskInfo createTurnRecordAndBuildTaskInfo(BusinessChatStartPlan startPlan) {
        return businessChatPersistenceService.createTurnRecordAndBuildTaskInfo(startPlan);
    }

    private BusinessChatRuntimeContext registerRuntimeWorkbench(BusinessChatTaskInfo taskInfo) {
        return businessChatRuntimeRegistry.register(taskInfo);
    }

    private Flux<ServerSentEvent<BusinessChatStreamEvent>> bindSseOutputChannel(BusinessChatRuntimeContext runtimeContext) {
        // SSE 出口：运行事件统一包装成 ServerSentEvent。
        return runtimeContext.getOutputChannel()
                .asFlux()
                .map(this::buildServerSentEvent)
                .doFinally(signalType -> runtimeContext.markOutputClosed());
    }

    private Mono<Void> startLeaseRenewalAndExecution(BusinessChatRuntimeContext runtimeContext) {
        Sinks.Empty<Void> executionCompletionSignal = Sinks.empty();
        // 租约流：执行完成信号停止续租。
        Mono<Void> executionFlow = assembleCompleteExecutionFlow(runtimeContext)
                .doFinally(signalType -> executionCompletionSignal.tryEmitEmpty());
        Mono<Void> leaseRenewalFlow = Flux.interval(LEASE_RENEW_INTERVAL, LEASE_RENEW_INTERVAL)
                .concatMap(sequence -> Mono.fromRunnable(() -> renewConversationLease(runtimeContext)))
                .takeUntilOther(executionCompletionSignal.asMono())
                .then();
        return Mono.when(executionFlow, leaseRenewalFlow);
    }

    private Mono<Void> assembleCompleteExecutionFlow(BusinessChatRuntimeContext runtimeContext) {
        // 主链路：执行计划 -> Agent -> 冻结快照 -> 事件补发 -> 成功归档 -> 完成事件。
        return Mono.fromRunnable(() -> pushExecutionProgress(runtimeContext, "正在加载会话记忆、改写问题并生成执行计划"))
                .then(Mono.fromSupplier(() -> orchestrateExecutionPlan(runtimeContext)))
                .doOnNext(executionPlan -> runtimeContext.setIntentAnalysis(buildIntentAnalysis(executionPlan)))
                .doOnNext(runtimeContext::setExecutionPlan)
                .doOnNext(executionPlan -> pushAgentStarted(runtimeContext, executionPlan))
                .flatMapMany(executionPlan -> businessChatAgentRegistry.getRequiredAgent(executionPlan.agentType())
                        .execute(runtimeContext, executionPlan))
                .concatMap(textDelta -> Mono.fromRunnable(() -> pushTextDeltaContinuously(runtimeContext, textDelta)))
                .then(Mono.fromRunnable(() -> pushAgentFinished(runtimeContext)))
                .then(Mono.fromRunnable(() -> pushFunctionSupplement(runtimeContext)))
                .then(finalizeSucceededTurn(runtimeContext));
    }

    private BusinessChatExecutionPlan orchestrateExecutionPlan(BusinessChatRuntimeContext runtimeContext) {
        return businessChatOrchestrator.orchestrate(runtimeContext);
    }

    private BusinessChatIntentAnalysis buildIntentAnalysis(BusinessChatExecutionPlan executionPlan) {
        return new BusinessChatIntentAnalysis(
                executionPlan.intentLabel(),
                executionPlan.intentReason(),
                executionPlan.executionMode());
    }

    private void pushAgentStarted(
            BusinessChatRuntimeContext runtimeContext,
            BusinessChatExecutionPlan executionPlan) {
        BusinessChatAgentEvent agentEvent = new BusinessChatAgentEvent(
                executionPlan.agentType(),
                executionPlan.agentType().getDisplayName(),
                "开始处理");
        emitAgentEvent(runtimeContext, BusinessChatEventType.AGENT_STARTED, agentEvent);
    }

    private void pushAgentFinished(BusinessChatRuntimeContext runtimeContext) {
        BusinessChatExecutionPlan executionPlan = runtimeContext.getExecutionPlan();
        BusinessChatAgentEvent agentEvent = new BusinessChatAgentEvent(
                executionPlan.agentType(),
                executionPlan.agentType().getDisplayName(),
                "处理完成");
        emitAgentEvent(runtimeContext, BusinessChatEventType.AGENT_FINISHED, agentEvent);
    }

    private void emitAgentEvent(
            BusinessChatRuntimeContext runtimeContext,
            BusinessChatEventType eventType,
            BusinessChatAgentEvent agentEvent) {
        runtimeContext.getReasoningNoteList().add(eventType.name() + ":" + agentEvent.agentName());
        emitStreamEvent(runtimeContext, new BusinessChatStreamEvent(
                eventType.name(),
                runtimeContext.getTaskInfo().conversationId(),
                runtimeContext.getTaskInfo().exchangeId(),
                runtimeContext.getTaskInfo().chatMode().getValue(),
                null,
                null,
                null,
                null,
                agentEvent.message(),
                agentEvent.agentType().getValue(),
                agentEvent.agentName(),
                runtimeContext.getFirstTokenLatencyMs() < 0 ? null : runtimeContext.getFirstTokenLatencyMs(),
                null));
    }

    private void renewConversationLease(BusinessChatRuntimeContext runtimeContext) {
        boolean renewed = redisLeaseManager.renew(
                runtimeContext.getTaskInfo().leaseKey(),
                runtimeContext.getTaskInfo().leaseOwnerToken(),
                runtimeContext.getTaskInfo().leaseTtl());
        if (!renewed) {
            throw new BaseException(
                    BusinessChatErrorCode.CHAT_LEASE_RENEW_FAILED,
                    "Failed to renew lease for conversation: " + runtimeContext.getTaskInfo().conversationId());
        }
    }

    private void pushTextDeltaContinuously(BusinessChatRuntimeContext runtimeContext, String textDelta) {
        // 正文流：增量写入回答缓冲区并同步发送 TEXT_DELTA。
        if (runtimeContext.markFirstTokenDelivered()) {
            runtimeContext.setFirstTokenLatencyMs(
                    System.currentTimeMillis() - runtimeContext.getTaskInfo().startAtEpochMillis());
        }
        runtimeContext.appendReplyContent(textDelta);
        runtimeContext.getReasoningNoteList().add("TEXT_DELTA:" + textDelta);
        emitStreamEvent(runtimeContext, new BusinessChatStreamEvent(
                BusinessChatEventType.TEXT_DELTA.name(),
                runtimeContext.getTaskInfo().conversationId(),
                runtimeContext.getTaskInfo().exchangeId(),
                runtimeContext.getTaskInfo().chatMode().getValue(),
                textDelta,
                null,
                null,
                null,
                null,
                runtimeContext.getFirstTokenLatencyMs() < 0 ? null : runtimeContext.getFirstTokenLatencyMs(),
                null));
    }

    private void pushExecutionProgress(BusinessChatRuntimeContext runtimeContext, String message) {
        runtimeContext.getReasoningNoteList().add("EXECUTION_PROGRESS:" + message);
        emitStreamEvent(runtimeContext, new BusinessChatStreamEvent(
                BusinessChatEventType.EXECUTION_PROGRESS.name(),
                runtimeContext.getTaskInfo().conversationId(),
                runtimeContext.getTaskInfo().exchangeId(),
                runtimeContext.getTaskInfo().chatMode().getValue(),
                null,
                null,
                null,
                null,
                message,
                runtimeContext.getFirstTokenLatencyMs() < 0 ? null : runtimeContext.getFirstTokenLatencyMs(),
                null));
    }

    private void pushFunctionSupplement(BusinessChatRuntimeContext runtimeContext) {
        // 补充流：前台只需要 Agent 与模型摘要，完整执行计划保留在 debugTraceJson。
        String functionSupplement = """
                Agent：%s
                执行模型：%s
                """.formatted(
                runtimeContext.getExecutionPlan().agentType().getDisplayName(),
                runtimeContext.getTaskInfo().modelConfig().displayName() + "/" + runtimeContext.getExecutionPlan().executionModel());
        runtimeContext.getToolTraceList().add(functionSupplement);
        emitStreamEvent(runtimeContext, new BusinessChatStreamEvent(
                BusinessChatEventType.FUNCTION_SUPPLEMENT.name(),
                runtimeContext.getTaskInfo().conversationId(),
                runtimeContext.getTaskInfo().exchangeId(),
                runtimeContext.getTaskInfo().chatMode().getValue(),
                null,
                functionSupplement,
                null,
                null,
                null,
                runtimeContext.getFirstTokenLatencyMs() < 0 ? null : runtimeContext.getFirstTokenLatencyMs(),
                null));
    }

    private Mono<Void> finalizeSucceededTurn(BusinessChatRuntimeContext runtimeContext) {
        return Mono.defer(() -> {
            if (!runtimeContext.markFinalized()) {
                return Mono.empty();
            }

            Long finalizeTraceStageId = null;
            try {
                // 收尾流：RuntimeContext -> FinalizedTurn -> trace/SSE/archive/summary。
                finalizeTraceStageId = businessChatPersistenceService.startTraceStage(
                        runtimeContext,
                        FINALIZE_STAGE_CODE,
                        "收尾归档",
                        900);
                BusinessChatFinalizedTurn frozenTurn = runtimeContext.freezeFinalizedTurn();
                BusinessChatFinalizedTurn finalizedTurn = finalizeTitleAndRecommendation(runtimeContext, frozenTurn);
                pushReferenceSupplement(runtimeContext, finalizedTurn);
                pushFollowUpRecommendations(runtimeContext, finalizedTurn);
                archiveSucceededTurn(finalizedTurn);
                businessChatPersistenceService.completeTraceStage(
                        finalizeTraceStageId,
                        "本轮回答、引用、推荐追问和调试快照已完成归档",
                        buildFinalizeTraceSnapshot(finalizedTurn));
                pushTurnFinished(runtimeContext, finalizedTurn);
                return refreshConversationSummary(finalizedTurn);
            } catch (Throwable error) {
                businessChatPersistenceService.failTraceStage(finalizeTraceStageId, error);
                return Mono.error(propagate(error));
            }
        });
    }

    private BusinessChatFinalizedTurn finalizeTitleAndRecommendation(
            BusinessChatRuntimeContext runtimeContext,
            BusinessChatFinalizedTurn frozenTurn) {
        Long recommendationTraceStageId = null;
        try {
            recommendationTraceStageId = businessChatPersistenceService.startTraceStage(
                    runtimeContext,
                    RECOMMENDATION_STAGE_CODE,
                    "推荐追问",
                    910);
            boolean titleRequired = !businessChatPersistenceService.dialogueTitleExists(frozenTurn);
            BusinessChatFinalizationResult finalizationResult =
                    businessChatFinalizationGenerator.generate(frozenTurn, titleRequired);
            if (titleRequired) {
                businessChatPersistenceService.updateDialogueTitleIfAbsent(
                        frozenTurn,
                        finalizationResult.dialogueTitle());
            }
            List<String> followUpSuggestionList = finalizationResult.followUpSuggestionList();
            runtimeContext.getFollowUpSuggestionList().clear();
            runtimeContext.getFollowUpSuggestionList().addAll(followUpSuggestionList);
            BusinessChatFinalizedTurn finalizedTurn = frozenTurn.withFollowUpSuggestionList(followUpSuggestionList);
            businessChatPersistenceService.completeTraceStage(
                    recommendationTraceStageId,
                    "推荐追问已生成",
                    followUpSuggestionList);
            return finalizedTurn;
        } catch (Throwable error) {
            businessChatPersistenceService.failTraceStage(recommendationTraceStageId, error);
            throw propagate(error);
        }
    }

    private RuntimeException propagate(Throwable error) {
        if (error instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        if (error instanceof Error fatalError) {
            throw fatalError;
        }
        return new IllegalStateException(error);
    }

    private Map<String, Object> buildFinalizeTraceSnapshot(BusinessChatFinalizedTurn finalizedTurn) {
        return Map.of(
                "replyLength", finalizedTurn.replyContent().length(),
                "sourceCount", finalizedTurn.sourceSnapshotList().size(),
                "followUpCount", finalizedTurn.followUpSuggestionList().size(),
                "totalLatencyMs", finalizedTurn.totalLatencyMs());
    }

    private void pushReferenceSupplement(BusinessChatRuntimeContext runtimeContext, BusinessChatFinalizedTurn finalizedTurn) {
        emitStreamEvent(runtimeContext, new BusinessChatStreamEvent(
                BusinessChatEventType.REFERENCE_SUPPLEMENT.name(),
                finalizedTurn.taskInfo().conversationId(),
                finalizedTurn.taskInfo().exchangeId(),
                finalizedTurn.taskInfo().chatMode().getValue(),
                null,
                null,
                finalizedTurn.sourceSnapshotList(),
                null,
                null,
                finalizedTurn.firstTokenLatencyMs() < 0 ? null : finalizedTurn.firstTokenLatencyMs(),
                null));
    }

    private void pushFollowUpRecommendations(
            BusinessChatRuntimeContext runtimeContext,
            BusinessChatFinalizedTurn finalizedTurn) {
        emitStreamEvent(runtimeContext, new BusinessChatStreamEvent(
                BusinessChatEventType.FOLLOW_UP_RECOMMENDATION.name(),
                finalizedTurn.taskInfo().conversationId(),
                finalizedTurn.taskInfo().exchangeId(),
                finalizedTurn.taskInfo().chatMode().getValue(),
                null,
                null,
                null,
                finalizedTurn.followUpSuggestionList(),
                null,
                finalizedTurn.firstTokenLatencyMs() < 0 ? null : finalizedTurn.firstTokenLatencyMs(),
                null));
    }

    private void archiveSucceededTurn(BusinessChatFinalizedTurn finalizedTurn) {
        businessChatPersistenceService.archiveSucceededTurn(finalizedTurn);
    }

    private Mono<Void> refreshConversationSummary(BusinessChatFinalizedTurn finalizedTurn) {
        return Mono.fromRunnable(() -> businessChatPersistenceService.refreshConversationSummary(finalizedTurn))
                .subscribeOn(Schedulers.boundedElastic())
                .doOnError(error -> log.error(
                        "Business chat summary refresh failed. conversationId={}, exchangeId={}",
                        finalizedTurn.taskInfo().conversationId(),
                        finalizedTurn.taskInfo().exchangeId(),
                        error))
                .then();
    }

    private void pushTurnFinished(BusinessChatRuntimeContext runtimeContext, BusinessChatFinalizedTurn finalizedTurn) {
        emitStreamEvent(runtimeContext, new BusinessChatStreamEvent(
                BusinessChatEventType.TURN_FINISHED.name(),
                finalizedTurn.taskInfo().conversationId(),
                finalizedTurn.taskInfo().exchangeId(),
                finalizedTurn.taskInfo().chatMode().getValue(),
                null,
                null,
                null,
                null,
                "本轮对话已完成",
                finalizedTurn.firstTokenLatencyMs() < 0 ? null : finalizedTurn.firstTokenLatencyMs(),
                finalizedTurn.totalLatencyMs()));
    }

    private Mono<Void> handleExecutionFailure(BusinessChatRuntimeContext runtimeContext, Throwable error) {
        // 失败流：冻结当前运行态，归档 FAILED 并发送 TURN_FAILED。
        log.error(
                "Business chat execution failed. conversationId={}, exchangeId={}",
                runtimeContext.getTaskInfo().conversationId(),
                runtimeContext.getTaskInfo().exchangeId(),
                error);
        return Mono.fromRunnable(() -> {
            businessChatPersistenceService.archiveFailedTurn(runtimeContext, error.getMessage());
            emitStreamEvent(runtimeContext, new BusinessChatStreamEvent(
                    BusinessChatEventType.TURN_FAILED.name(),
                    runtimeContext.getTaskInfo().conversationId(),
                    runtimeContext.getTaskInfo().exchangeId(),
                    runtimeContext.getTaskInfo().chatMode().getValue(),
                    null,
                    null,
                    null,
                    null,
                    error.getMessage(),
                    runtimeContext.getFirstTokenLatencyMs() < 0 ? null : runtimeContext.getFirstTokenLatencyMs(),
                    System.currentTimeMillis() - runtimeContext.getTaskInfo().startAtEpochMillis()));
        });
    }

    private void handleExecutionCancellation(BusinessChatRuntimeContext runtimeContext, SignalType signalType) {
        if (signalType != SignalType.CANCEL || !runtimeContext.markFinalized()) {
            return;
        }
        String finishNote = "本轮回答已中止";
        log.warn(
                "Business chat execution cancelled. conversationId={}, exchangeId={}",
                runtimeContext.getTaskInfo().conversationId(),
                runtimeContext.getTaskInfo().exchangeId());
        businessChatPersistenceService.archiveStoppedTurn(runtimeContext, finishNote);
    }

    private void handleStartFailure(
            BusinessChatStartPlan startPlan,
            BusinessChatTaskInfo taskInfo,
            BusinessChatRuntimeContext runtimeContext,
            Throwable error) {
        log.error("Business chat startup failed. conversationId={}", startPlan.conversationId(), error);
        try {
            // 启动失败流：已创建 exchange 时收束为 FAILED。
            if (taskInfo != null) {
                BusinessChatRuntimeContext archiveContext = runtimeContext;
                if (archiveContext == null) {
                    archiveContext =
                            new BusinessChatRuntimeContext(taskInfo, Sinks.many().unicast().onBackpressureBuffer());
                }
                businessChatPersistenceService.archiveFailedTurn(archiveContext, error.getMessage());
            }
        } finally {
            try {
                if (runtimeContext != null) {
                    businessChatRuntimeRegistry.unregister(runtimeContext.getTaskInfo().conversationId());
                }
            } finally {
                boolean released = redisLeaseManager.release(startPlan.leaseKey(), startPlan.leaseOwnerToken());
                if (!released) {
                    log.error(
                            "Conversation lease release failed during startup rollback. conversationId={}, leaseKey={}",
                            startPlan.conversationId(),
                            startPlan.leaseKey());
                }
            }
        }
    }

    private void releaseRuntimeResources(BusinessChatRuntimeContext runtimeContext) {
        try {
            // 清理流：关闭 SSE，注销运行态，释放会话租约。
            runtimeContext.getOutputChannel().tryEmitComplete();
        } finally {
            try {
                businessChatRuntimeRegistry.unregister(runtimeContext.getTaskInfo().conversationId());
            } finally {
                boolean released = redisLeaseManager.release(
                        runtimeContext.getTaskInfo().leaseKey(),
                        runtimeContext.getTaskInfo().leaseOwnerToken());
                if (!released) {
                    log.error(
                            "Conversation lease release failed. conversationId={}, leaseKey={}",
                            runtimeContext.getTaskInfo().conversationId(),
                            runtimeContext.getTaskInfo().leaseKey());
                }
            }
        }
    }

    private Flux<ServerSentEvent<BusinessChatStreamEvent>> buildRejectedStream(
            BusinessChatStartPlan startPlan,
            String message) {
        return Flux.just(buildServerSentEvent(new BusinessChatStreamEvent(
                BusinessChatEventType.TURN_REJECTED.name(),
                startPlan.conversationId(),
                null,
                startPlan.chatMode().getValue(),
                null,
                null,
                null,
                null,
                message,
                null,
                null)));
    }

    private void emitStreamEvent(BusinessChatRuntimeContext runtimeContext, BusinessChatStreamEvent event) {
        if (runtimeContext.isOutputClosed()) {
            return;
        }
        Sinks.EmitResult emitResult = runtimeContext.getOutputChannel().tryEmitNext(event);
        if (emitResult == Sinks.EmitResult.FAIL_CANCELLED || emitResult == Sinks.EmitResult.FAIL_TERMINATED) {
            runtimeContext.markOutputClosed();
        }
    }

    private ServerSentEvent<BusinessChatStreamEvent> buildServerSentEvent(BusinessChatStreamEvent event) {
        return ServerSentEvent.<BusinessChatStreamEvent>builder()
                .event(event.eventType())
                .data(event)
                .build();
    }

    private String normalizeRequiredText(String value, String fieldName) {
        String normalized = value == null ? null : value.strip();
        if (!StringUtils.hasText(normalized)) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }
}
