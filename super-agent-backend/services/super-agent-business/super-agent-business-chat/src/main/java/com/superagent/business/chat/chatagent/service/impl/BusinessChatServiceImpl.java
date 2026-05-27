package com.superagent.business.chat.chatagent.service.impl;

import com.superagent.business.chat.chatagent.execution.agent.BusinessChatAgentEvent;
import com.superagent.business.chat.chatagent.execution.agent.BusinessChatAgentRegistry;
import com.superagent.business.chat.chatagent.api.dto.BusinessChatStreamRequest;
import com.superagent.business.chat.chatagent.logging.BusinessChatBusinessFlowLogger;
import com.superagent.business.chat.chatagent.orchestration.finalization.BusinessChatFinalizationGenerator;
import com.superagent.business.chat.chatagent.orchestration.finalization.BusinessChatFinalizationResult;
import com.superagent.business.chat.chatagent.orchestration.model.BusinessChatAgentStep;
import com.superagent.business.chat.chatagent.runtime.BusinessChatConversationLeaseKeys;
import com.superagent.business.chat.chatagent.persistence.model.BusinessChatEventType;
import com.superagent.business.chat.chatagent.orchestration.model.BusinessChatExecutionPlan;
import com.superagent.business.chat.chatagent.orchestration.model.BusinessChatIntentAnalysis;
import com.superagent.business.chat.chatagent.orchestration.model.BusinessChatMode;
import com.superagent.business.chat.chatagent.execution.model.BusinessChatModelApiConfigSnapshot;
import com.superagent.business.chat.chatagent.orchestration.model.BusinessChatStartPlan;
import com.superagent.business.chat.chatagent.runtime.BusinessChatTaskInfo;
import com.superagent.business.chat.chatagent.orchestration.BusinessChatOrchestrator;
import com.superagent.business.chat.chatagent.persistence.BusinessChatPersistenceService;
import com.superagent.business.chat.chatagent.runtime.BusinessChatFinalizedTurn;
import com.superagent.business.chat.chatagent.runtime.BusinessChatRuntimeContext;
import com.superagent.business.chat.chatagent.runtime.BusinessChatRuntimeRegistry;
import com.superagent.business.chat.chatagent.service.BusinessChatErrorCode;
import com.superagent.business.chat.chatagent.service.BusinessChatModelApiConfigService;
import com.superagent.business.chat.chatagent.service.BusinessChatService;
import com.superagent.business.chat.chatagent.trace.BusinessChatTraceStage;
import com.superagent.business.chat.chatagent.trace.BusinessChatTraceStageRunner;
import com.superagent.business.chat.chatagent.api.vo.BusinessChatStreamEvent;
import com.superagent.business.chat.auth.AuthRole;
import com.superagent.business.chat.auth.AuthSessionContext;
import com.superagent.business.chat.auth.AuthSessionHolder;
import com.superagent.business.chat.support.BusinessInputValidator;
import com.superagent.business.chat.knowledge.api.dto.KnowledgeDocumentIdRequest;
import com.superagent.business.chat.knowledge.retrieval.KnowledgeRetrievalParentEvidence;
import com.superagent.business.chat.knowledge.document.service.KnowledgeManageService;
import com.superagent.business.chat.knowledge.api.vo.KnowledgeDocumentVo;
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

/**
 * 流式问答主服务。
 *
 * <p>这是一次业务对话请求的总入口，核心目标不是“直接调用模型”，而是把一次流式请求收束成
 * 可串行、可追踪、可归档、可恢复展示的完整业务链路。</p>
 *
 * <p>全链路顺序：</p>
 * <ol>
 *     <li>把前端请求归一化成 {@link BusinessChatStartPlan}，固定本轮 conversation、model、document 快照。</li>
 *     <li>获取 Redis 会话租约，保证同一个 conversationId 同一时间只执行一轮。</li>
 *     <li>创建 RUNNING exchange，并注册 {@link BusinessChatRuntimeContext} 作为本轮内存工作台。</li>
 *     <li>编排执行计划、调用 Agent、持续向前端发送 SSE 增量事件。</li>
 *     <li>执行完成后冻结运行态，补发引用和推荐追问，归档成功终态并刷新摘要。</li>
 *     <li>失败或客户端断开时，同样冻结当前状态，归档 FAILED/STOPPED，并释放运行态和租约。</li>
 * </ol>
 *
 * <p>这个类只负责编排生命周期，不直接拼模型提示词、不直接写知识图谱；这些职责分别在
 * Orchestrator、Agent、Persistence、KnowledgeManageService 中完成。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BusinessChatServiceImpl implements BusinessChatService {

    private static final Duration CONVERSATION_LEASE_TTL = Duration.ofSeconds(30);

    private static final Duration LEASE_RENEW_INTERVAL = Duration.ofSeconds(10);

    private final RedisLeaseManager redisLeaseManager;

    private final BusinessChatPersistenceService businessChatPersistenceService;

    private final BusinessChatRuntimeRegistry businessChatRuntimeRegistry;

    private final BusinessChatOrchestrator businessChatOrchestrator;

    private final BusinessChatAgentRegistry businessChatAgentRegistry;

    private final BusinessChatFinalizationGenerator businessChatFinalizationGenerator;

    private final BusinessChatModelApiConfigService modelApiConfigService;

    private final KnowledgeManageService knowledgeManageService;

    private final BusinessChatTraceStageRunner traceStageRunner;

    private final BusinessChatBusinessFlowLogger businessFlowLogger;

    @Override
    public Flux<ServerSentEvent<BusinessChatStreamEvent>> streamChat(BusinessChatStreamRequest request) {
        return Flux.defer(() -> startDeferredChatStream(request));
    }

    /**
     * 启动一轮延迟执行的 SSE 流。
     *
     * <p>返回给前端的是 outputFlux；真正的模型执行、归档和资源释放在 executionFlux 中进行。
     * 两条流通过 RuntimeContext 的 outputChannel 连接，所以执行链路可以持续推事件，
     * 前端连接断开时也能由 doFinally 收束成 STOPPED/FAILED。</p>
     */
    private Flux<ServerSentEvent<BusinessChatStreamEvent>> startDeferredChatStream(BusinessChatStreamRequest request) {
        // 输入流：请求参数 -> StartPlan。StartPlan 是本轮对话的不可变入口快照，
        // 后续建档、租约、运行态、归档都从这里取同一组 conversation/model/document 信息。
        BusinessChatStartPlan startPlan = normalizeRequestAndBuildStartPlan(request);
        businessFlowLogger.logStart(startPlan);
        if (!tryAcquireConversationLease(startPlan)) {
            return buildRejectedStream(startPlan, "该会话当前正在执行中");
        }
        BusinessChatTaskInfo taskInfo = null;
        BusinessChatRuntimeContext runtimeContext = null;
        try {
            // 运行流：StartPlan -> RUNNING exchange -> RuntimeContext。
            // exchange 先落 RUNNING，保证模型流中途失败或用户断开时仍有可归档的数据库主键。
            taskInfo = createTurnRecordAndBuildTaskInfo(startPlan);
            runtimeContext = registerRuntimeWorkbench(taskInfo);
            BusinessChatRuntimeContext boundRuntimeContext = runtimeContext;
            Flux<ServerSentEvent<BusinessChatStreamEvent>> outputFlux = bindSseOutputChannel(boundRuntimeContext);
            // 输出流和执行流拆开：outputFlux 只负责把运行态事件吐给前端；
            // executionFlux 负责后台执行、归档和资源释放，二者通过 runtimeContext.outputChannel 连接。
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
        String question = BusinessInputValidator.normalizeRequiredText(request.getQuestion(), "question");
        String conversationId = normalizeOptionalConversationId(request.getConversationId());
        BusinessChatMode chatMode = BusinessChatMode.fromValue(request.getChatMode());
        BusinessChatModelApiConfigSnapshot modelConfig =
                modelApiConfigService.getRequiredAvailableSnapshot(request.getModelConfigId());
        String workspaceId = BusinessInputValidator.normalizeRequiredText(request.getWorkspaceId(), "workspaceId");
        AuthSessionContext session = AuthSessionHolder.required();
        String authSessionToken = session.role() == AuthRole.GUEST ? session.token() : "";
        // 当前文档模式在入口绑定文档快照。后续执行全程使用同一个 documentId/name，
        // 避免中途前端切换选择后影响已经开始的一轮回答。
        KnowledgeDocumentVo selectedDocument = loadSelectedDocument(chatMode, workspaceId, request.getSelectedDocumentId());
        long startAtEpochMillis = System.currentTimeMillis();
        return new BusinessChatStartPlan(
                question,
                conversationId,
                workspaceId,
                authSessionToken,
                chatMode,
                modelConfig,
                selectedDocument == null ? null : Long.valueOf(selectedDocument.getDocumentId()),
                selectedDocument == null ? null : selectedDocument.getDocumentName(),
                UUID.randomUUID().toString().replace("-", ""),
                BusinessChatConversationLeaseKeys.conversationLeaseKey(conversationId),
                UUID.randomUUID().toString(),
                CONVERSATION_LEASE_TTL,
                startAtEpochMillis);
    }

    private KnowledgeDocumentVo loadSelectedDocument(
            BusinessChatMode chatMode,
            String workspaceId,
            String selectedDocumentId) {
        if (chatMode != BusinessChatMode.CURRENT_DOCUMENT) {
            return null;
        }
        long documentId = BusinessInputValidator.parsePositiveLong(selectedDocumentId, "selectedDocumentId");
        KnowledgeDocumentIdRequest request = new KnowledgeDocumentIdRequest();
        request.setDocumentId(String.valueOf(documentId));
        request.setWorkspaceId(workspaceId);
        return knowledgeManageService.queryDocumentDetail(request);
    }

    private String normalizeOptionalConversationId(String conversationId) {
        String normalizedConversationId = BusinessInputValidator.normalizeOptionalText(conversationId);
        return normalizedConversationId != null
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

    /**
     * 并行运行主执行流和租约续期流。
     *
     * <p>租约不是业务状态，而是并发控制边界：只要本轮回答还没结束，就要持续续租；
     * 一旦主执行流完成，executionCompletionSignal 会停止续租流。</p>
     */
    private Mono<Void> startLeaseRenewalAndExecution(BusinessChatRuntimeContext runtimeContext) {
        Sinks.Empty<Void> executionCompletionSignal = Sinks.empty();
        // 租约流：执行完成信号停止续租。续租和主执行并行，是为了长回答期间仍保持 conversationId 独占。
        Mono<Void> executionFlow = assembleCompleteExecutionFlow(runtimeContext)
                .doFinally(signalType -> executionCompletionSignal.tryEmitEmpty());
        // 会话租约保护同一 conversationId 的串行执行；续租失败说明独占权丢失，本轮必须失败归档。
        Mono<Void> leaseRenewalFlow = Flux.interval(LEASE_RENEW_INTERVAL, LEASE_RENEW_INTERVAL)
                .concatMap(sequence -> Mono.fromRunnable(() -> renewConversationLease(runtimeContext)))
                .takeUntilOther(executionCompletionSignal.asMono())
                .then();
        return Mono.when(executionFlow, leaseRenewalFlow);
    }

    /**
     * 组装本轮对话的主执行链路。
     *
     * <p>这里的顺序就是前端和数据库共同认可的事件顺序：
     * 先产生执行计划，再由 Agent 输出正文，再补充执行信息，最后冻结并归档终态。</p>
     */
    private Mono<Void> assembleCompleteExecutionFlow(BusinessChatRuntimeContext runtimeContext) {
        // 主链路：执行计划 -> Agent -> 正文增量 -> 冻结快照 -> 补发引用/追问 -> 成功归档 -> 完成事件。
        // 这里保持线性 concat/then 顺序，保证前端看到的事件顺序和数据库终态一致。
        return Mono.fromRunnable(() -> pushExecutionProgress(runtimeContext, "正在加载会话记忆、改写问题并生成执行计划"))
                .then(Mono.fromSupplier(() -> orchestrateExecutionPlan(runtimeContext)))
                .doOnNext(executionPlan -> businessFlowLogger.logExecutionPlan(runtimeContext, executionPlan))
                .doOnNext(executionPlan -> runtimeContext.setIntentAnalysis(buildIntentAnalysis(executionPlan)))
                .doOnNext(runtimeContext::setExecutionPlan)
                .doOnNext(executionPlan -> applyRetrievalSourceSnapshots(runtimeContext, executionPlan))
                .flatMapMany(executionPlan -> executeAgentWithTrace(runtimeContext, executionPlan))
                .concatMap(textDelta -> Mono.fromRunnable(() -> pushTextDeltaContinuously(runtimeContext, textDelta)))
                .then(Mono.fromRunnable(() -> pushFunctionSupplement(runtimeContext)))
                .then(finalizeSucceededTurn(runtimeContext));
    }

    private Flux<String> executeAgentWithTrace(
            BusinessChatRuntimeContext runtimeContext,
            BusinessChatExecutionPlan executionPlan) {
        Long answerGenerationTraceStageId = null;
        try {
            answerGenerationTraceStageId = traceStageRunner.start(
                    runtimeContext,
                    BusinessChatTraceStage.ANSWER_GENERATION);
            Long boundTraceStageId = answerGenerationTraceStageId;
            return executeAgentStepList(runtimeContext, executionPlan, boundTraceStageId)
                    .doOnComplete(() -> traceStageRunner.complete(
                            boundTraceStageId,
                            executionPlan.shortCircuit() ? "证据不足，本轮未调用模型生成" : "回答生成完成",
                            buildAnswerGenerationTraceSnapshot(runtimeContext, executionPlan)))
                    .doOnError(error -> traceStageRunner.fail(boundTraceStageId, error));
        } catch (Throwable error) {
            traceStageRunner.fail(answerGenerationTraceStageId, error);
            return Flux.error(propagate(error));
        }
    }

    private Flux<String> executeAgentStepList(
            BusinessChatRuntimeContext runtimeContext,
            BusinessChatExecutionPlan executionPlan,
            Long parentTraceStageId) {
        Flux<String> outputFlux = Flux.empty();
        for (BusinessChatAgentStep agentStep : executionPlan.agentStepList()) {
            outputFlux = outputFlux.concatWith(executeAgentStep(runtimeContext, executionPlan, parentTraceStageId, agentStep));
        }
        return outputFlux;
    }

    private Flux<String> executeAgentStep(
            BusinessChatRuntimeContext runtimeContext,
            BusinessChatExecutionPlan executionPlan,
            Long parentTraceStageId,
            BusinessChatAgentStep agentStep) {
        return Flux.defer(() -> {
            Long traceStageId = traceStageRunner.startSubStage(
                    runtimeContext,
                    parentTraceStageId,
                    agentStep.stageCode(),
                    agentStep.stageName(),
                    agentStep.stageOrder());
            pushAgentStarted(runtimeContext, agentStep);
            runtimeContext.bindCurrentTraceStage(agentStep.stageCode(), agentStep.stageName());
            Flux<String> agentFlux = agentStep.answerProducer()
                    ? businessChatAgentRegistry.getRequiredAgent(agentStep.agentType()).execute(runtimeContext, executionPlan)
                    : executeNonAnswerAgentStep(runtimeContext, executionPlan, agentStep);
            return agentFlux
                    .doOnComplete(() -> {
                        traceStageRunner.complete(
                                traceStageId,
                                agentStep.stageName() + "处理完成",
                                buildAgentStepTraceSnapshot(runtimeContext, executionPlan, agentStep));
                        pushAgentFinished(runtimeContext, agentStep);
                    })
                    .doOnError(error -> traceStageRunner.fail(traceStageId, error))
                    .doFinally(signalType -> runtimeContext.clearCurrentTraceStage());
        });
    }

    private Flux<String> executeNonAnswerAgentStep(
            BusinessChatRuntimeContext runtimeContext,
            BusinessChatExecutionPlan executionPlan,
            BusinessChatAgentStep agentStep) {
        return Mono.fromRunnable(() -> validateNonAnswerAgentStep(runtimeContext, executionPlan, agentStep))
                .thenMany(Flux.empty());
    }

    private void validateNonAnswerAgentStep(
            BusinessChatRuntimeContext runtimeContext,
            BusinessChatExecutionPlan executionPlan,
            BusinessChatAgentStep agentStep) {
        switch (agentStep.agentType()) {
            case EVIDENCE_GENERATION -> {
                if (executionPlan.retrievalEvidenceList() == null || executionPlan.retrievalEvidenceList().isEmpty()) {
                    throw new IllegalStateException("retrieval evidence is required for evidence generation agent.");
                }
            }
            case CITATION -> {
                if (executionPlan.retrievalEvidenceList() == null || executionPlan.retrievalEvidenceList().isEmpty()) {
                    throw new IllegalStateException("retrieval evidence is required for citation agent.");
                }
                if (runtimeContext.getSourceSnapshotList().isEmpty()) {
                    throw new IllegalStateException("source snapshot is required for citation agent.");
                }
            }
            default -> throw new IllegalStateException("non-answer agent is not supported: " + agentStep.agentType().getValue());
        }
    }

    private Map<String, Object> buildAgentStepTraceSnapshot(
            BusinessChatRuntimeContext runtimeContext,
            BusinessChatExecutionPlan executionPlan,
            BusinessChatAgentStep agentStep) {
        return Map.of(
                "agentType", agentStep.agentType().getValue(),
                "answerProducer", agentStep.answerProducer(),
                "executionMode", executionPlan.executionMode().getValue(),
                "executionModel", executionPlan.executionModel(),
                "evidenceCount", executionPlan.retrievalEvidenceList() == null
                        ? 0
                        : executionPlan.retrievalEvidenceList().size(),
                "replyLength", runtimeContext.getReplyContent().length());
    }

    private Map<String, Object> buildAnswerGenerationTraceSnapshot(
            BusinessChatRuntimeContext runtimeContext,
            BusinessChatExecutionPlan executionPlan) {
        return Map.of(
                "agentCount", executionPlan.agentStepList().size(),
                "answerAgentType", executionPlan.answerAgentStep().agentType().getValue(),
                "executionMode", executionPlan.executionMode().getValue(),
                "executionModel", executionPlan.executionModel(),
                "knowledgeRoute", executionPlan.knowledgeRoute(),
                "shortCircuit", executionPlan.shortCircuit(),
                "replyLength", runtimeContext.getReplyContent().length());
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

    private void applyRetrievalSourceSnapshots(
            BusinessChatRuntimeContext runtimeContext,
            BusinessChatExecutionPlan executionPlan) {
        runtimeContext.getSourceSnapshotList().clear();
        List<KnowledgeRetrievalParentEvidence> evidenceList = executionPlan.retrievalEvidenceList();
        if (evidenceList == null || evidenceList.isEmpty()) {
            return;
        }
        for (int index = 0; index < evidenceList.size(); index++) {
            KnowledgeRetrievalParentEvidence evidence = evidenceList.get(index);
            runtimeContext.getSourceSnapshotList().add(renderRetrievalSourceSnapshot(evidence, index + 1));
        }
    }

    private String renderRetrievalSourceSnapshot(KnowledgeRetrievalParentEvidence evidence, int evidenceNumber) {
        return new StringBuilder()
                .append("[").append(evidenceNumber).append("]\n")
                .append("文档名称：").append(nullToEmpty(evidence.documentName())).append("\n")
                .append("章节路径：").append(nullToEmpty(evidence.sectionPath())).append("\n")
                .append("引用内容：\n").append(nullToEmpty(evidence.parentText()))
                .toString();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private void pushAgentStarted(
            BusinessChatRuntimeContext runtimeContext,
            BusinessChatAgentStep agentStep) {
        BusinessChatAgentEvent agentEvent = new BusinessChatAgentEvent(
                agentStep.agentType(),
                agentStep.stageName(),
                "开始处理");
        emitAgentEvent(runtimeContext, BusinessChatEventType.AGENT_STARTED, agentEvent);
    }

    private void pushAgentFinished(
            BusinessChatRuntimeContext runtimeContext,
            BusinessChatAgentStep agentStep) {
        BusinessChatAgentEvent agentEvent = new BusinessChatAgentEvent(
                agentStep.agentType(),
                agentStep.stageName(),
                "处理完成");
        emitAgentEvent(runtimeContext, BusinessChatEventType.AGENT_FINISHED, agentEvent);
    }

    private void emitAgentEvent(
            BusinessChatRuntimeContext runtimeContext,
            BusinessChatEventType eventType,
            BusinessChatAgentEvent agentEvent) {
        runtimeContext.getReasoningNoteList().add(eventType.name() + ":" + agentEvent.agentName());
        emitStreamEvent(runtimeContext, BusinessChatStreamEvent.agent(
                eventType.name(),
                runtimeContext.getTaskInfo().conversationId(),
                runtimeContext.getTaskInfo().exchangeId(),
                runtimeContext.getTaskInfo().chatMode().getValue(),
                agentEvent.message(),
                agentEvent.agentType().getValue(),
                agentEvent.agentName(),
                firstTokenLatency(runtimeContext)));
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
        // 正文流：每个 token delta 都先进入回答缓冲区，再同步发送 TEXT_DELTA。
        // 归档读取的是缓冲区完整正文，所以不能只向前端发而不写 runtimeContext。
        if (runtimeContext.markFirstTokenDelivered()) {
            runtimeContext.setFirstTokenLatencyMs(
                    System.currentTimeMillis() - runtimeContext.getTaskInfo().startAtEpochMillis());
        }
        runtimeContext.appendReplyContent(textDelta);
        runtimeContext.getReasoningNoteList().add("TEXT_DELTA:" + textDelta);
        emitStreamEvent(runtimeContext, BusinessChatStreamEvent.textDelta(
                runtimeContext.getTaskInfo().conversationId(),
                runtimeContext.getTaskInfo().exchangeId(),
                runtimeContext.getTaskInfo().chatMode().getValue(),
                textDelta,
                firstTokenLatency(runtimeContext)));
    }

    private void pushExecutionProgress(BusinessChatRuntimeContext runtimeContext, String message) {
        runtimeContext.getReasoningNoteList().add("EXECUTION_PROGRESS:" + message);
        emitStreamEvent(runtimeContext, BusinessChatStreamEvent.message(
                BusinessChatEventType.EXECUTION_PROGRESS.name(),
                runtimeContext.getTaskInfo().conversationId(),
                runtimeContext.getTaskInfo().exchangeId(),
                runtimeContext.getTaskInfo().chatMode().getValue(),
                message,
                firstTokenLatency(runtimeContext),
                null));
    }

    private void pushFunctionSupplement(BusinessChatRuntimeContext runtimeContext) {
        // 补充流：前台只需要 Agent 与模型摘要；完整执行计划进入 debugTraceJson，供后台追踪页复盘。
        String functionSupplement = """
                Agent：
                %s
                执行模型：%s
                """.formatted(
                renderAgentStepSupplement(runtimeContext.getExecutionPlan().agentStepList()),
                runtimeContext.getTaskInfo().modelConfig().displayName() + "/" + runtimeContext.getExecutionPlan().executionModel());
        runtimeContext.getToolTraceList().add(functionSupplement);
        emitStreamEvent(runtimeContext, BusinessChatStreamEvent.functionSupplement(
                runtimeContext.getTaskInfo().conversationId(),
                runtimeContext.getTaskInfo().exchangeId(),
                runtimeContext.getTaskInfo().chatMode().getValue(),
                functionSupplement,
                firstTokenLatency(runtimeContext)));
    }

    private String renderAgentStepSupplement(List<BusinessChatAgentStep> agentStepList) {
        return agentStepList.stream()
                .map(agentStep -> "- " + agentStep.stageName())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("- 无");
    }

    /**
     * 成功收尾并归档一轮回答。
     *
     * <p>该方法是成功路径唯一能把 RUNNING exchange 变成 COMPLETED 的位置。
     * markFinalized 用来保证成功、失败、中止三条路径只有一条能真正归档。</p>
     */
    private Mono<Void> finalizeSucceededTurn(BusinessChatRuntimeContext runtimeContext) {
        return Mono.defer(() -> {
            if (!runtimeContext.markFinalized()) {
                return Mono.empty();
            }

            try {
                // 收尾流：RuntimeContext -> FinalizedTurn -> trace/SSE/archive/summary。
                // FinalizedTurn 是冻结快照，后续引用、推荐追问、归档和摘要都读它，避免各环节读到不同运行态。
                BusinessChatFinalizedTurn frozenTurn = runtimeContext.freezeFinalizedTurn();
                BusinessChatFinalizedTurn finalizedTurn = finalizeTitleAndRecommendation(runtimeContext, frozenTurn);
                BusinessChatFinalizedTurn archivedTurn = traceStageRunner.run(
                        runtimeContext,
                        BusinessChatTraceStage.FINALIZE,
                        () -> {
                            pushReferenceSupplement(runtimeContext, finalizedTurn);
                            pushFollowUpRecommendations(runtimeContext, finalizedTurn);
                            archiveSucceededTurn(finalizedTurn);
                            return finalizedTurn;
                        },
                        this::buildFinalizeTraceSnapshot,
                        turn -> "本轮回答、引用、推荐问题和调试快照已完成归档");
                pushTurnFinished(runtimeContext, archivedTurn);
                businessFlowLogger.logFinished(archivedTurn);
                return refreshConversationSummary(archivedTurn);
            } catch (Throwable error) {
                return Mono.error(propagate(error));
            }
        });
    }

    /**
     * 生成首轮标题和本轮推荐追问。
     *
     * <p>标题只在会话还没有标题时写入；推荐追问会回填到 RuntimeContext，
     * 然后随 FinalizedTurn 一起进入 SSE 和数据库归档。</p>
     */
    private BusinessChatFinalizedTurn finalizeTitleAndRecommendation(
            BusinessChatRuntimeContext runtimeContext,
            BusinessChatFinalizedTurn frozenTurn) {
        return traceStageRunner.run(
                runtimeContext,
                BusinessChatTraceStage.RECOMMENDATION,
                () -> {
                    boolean titleRequired = !businessChatPersistenceService.dialogueTitleExists(frozenTurn);
                    BusinessChatFinalizationResult finalizationResult =
                            businessChatFinalizationGenerator.generate(runtimeContext, frozenTurn, titleRequired);
                    if (titleRequired) {
                        // 标题只允许首轮补写，避免后续轮次覆盖用户已经看到的会话名称。
                        businessChatPersistenceService.updateDialogueTitleIfAbsent(
                                frozenTurn,
                                finalizationResult.dialogueTitle());
                    }
                    List<String> followUpSuggestionList = finalizationResult.followUpSuggestionList();
                    runtimeContext.getFollowUpSuggestionList().clear();
                    runtimeContext.getFollowUpSuggestionList().addAll(followUpSuggestionList);
                    return frozenTurn.withFollowUpSuggestionList(followUpSuggestionList);
                },
                BusinessChatFinalizedTurn::followUpSuggestionList,
                turn -> "推荐问题已生成");
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
                "modelCallCount", finalizedTurn.modelCallCount(),
                "totalLatencyMs", finalizedTurn.totalLatencyMs());
    }

    private void pushReferenceSupplement(BusinessChatRuntimeContext runtimeContext, BusinessChatFinalizedTurn finalizedTurn) {
        emitStreamEvent(runtimeContext, BusinessChatStreamEvent.sourceSnapshotList(
                finalizedTurn.taskInfo().conversationId(),
                finalizedTurn.taskInfo().exchangeId(),
                finalizedTurn.taskInfo().chatMode().getValue(),
                finalizedTurn.sourceSnapshotList(),
                firstTokenLatency(finalizedTurn)));
    }

    private void pushFollowUpRecommendations(
            BusinessChatRuntimeContext runtimeContext,
            BusinessChatFinalizedTurn finalizedTurn) {
        emitStreamEvent(runtimeContext, BusinessChatStreamEvent.followUpSuggestionList(
                finalizedTurn.taskInfo().conversationId(),
                finalizedTurn.taskInfo().exchangeId(),
                finalizedTurn.taskInfo().chatMode().getValue(),
                finalizedTurn.followUpSuggestionList(),
                firstTokenLatency(finalizedTurn)));
    }

    private void archiveSucceededTurn(BusinessChatFinalizedTurn finalizedTurn) {
        businessChatPersistenceService.archiveSucceededTurn(finalizedTurn);
    }

    private Mono<Void> refreshConversationSummary(BusinessChatFinalizedTurn finalizedTurn) {
        // 摘要刷新基于已成功归档的 frozen turn 异步执行。
        // 它不是本轮回答成功的前置条件，所以失败只记录日志，不反向改写 exchange 终态。
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
        emitStreamEvent(runtimeContext, BusinessChatStreamEvent.message(
                BusinessChatEventType.TURN_FINISHED.name(),
                finalizedTurn.taskInfo().conversationId(),
                finalizedTurn.taskInfo().exchangeId(),
                finalizedTurn.taskInfo().chatMode().getValue(),
                "本轮对话已完成",
                firstTokenLatency(finalizedTurn),
                finalizedTurn.totalLatencyMs()));
    }

    /**
     * 失败路径归档。
     *
     * <p>模型异常、租约续期失败、编排异常都会进入这里。
     * 它不会丢弃已经流出的正文，而是把当前 RuntimeContext 冻结后连同失败原因一起归档。</p>
     */
    private Mono<Void> handleExecutionFailure(BusinessChatRuntimeContext runtimeContext, Throwable error) {
        // 失败流：冻结当前运行态，归档 FAILED 并发送 TURN_FAILED。
        // 即使正文已经输出一部分，也要把已输出内容和失败原因一起落库，方便前端刷新后复盘。
        log.error(
                "Business chat execution failed. conversationId={}, exchangeId={}",
                runtimeContext.getTaskInfo().conversationId(),
                runtimeContext.getTaskInfo().exchangeId(),
                error);
        businessFlowLogger.logFailed(runtimeContext, error);
        return Mono.fromRunnable(() -> {
            businessChatPersistenceService.archiveFailedTurn(runtimeContext, error.getMessage());
            emitStreamEvent(runtimeContext, BusinessChatStreamEvent.message(
                    BusinessChatEventType.TURN_FAILED.name(),
                    runtimeContext.getTaskInfo().conversationId(),
                    runtimeContext.getTaskInfo().exchangeId(),
                    runtimeContext.getTaskInfo().chatMode().getValue(),
                    error.getMessage(),
                    firstTokenLatency(runtimeContext),
                    System.currentTimeMillis() - runtimeContext.getTaskInfo().startAtEpochMillis()));
        });
    }

    /**
     * 客户端主动断开时的中止归档。
     *
     * <p>Reactor 的 CANCEL 只说明下游不再接收 SSE，不代表服务端代码可以不收尾；
     * 因此这里把本轮记录为 STOPPED，保留已生成内容。</p>
     */
    private void handleExecutionCancellation(BusinessChatRuntimeContext runtimeContext, SignalType signalType) {
        if (signalType != SignalType.CANCEL || !runtimeContext.markFinalized()) {
            return;
        }
        String finishNote = "本轮回答已中止";
        log.warn(
                "Business chat execution cancelled. conversationId={}, exchangeId={}",
                runtimeContext.getTaskInfo().conversationId(),
                runtimeContext.getTaskInfo().exchangeId());
        businessFlowLogger.logStopped(runtimeContext);
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
            releaseConversationResources(
                    runtimeContext,
                    startPlan.conversationId(),
                    startPlan.leaseKey(),
                    startPlan.leaseOwnerToken(),
                    "Conversation lease release failed during startup rollback. conversationId={}, leaseKey={}");
        }
    }

    private void releaseRuntimeResources(BusinessChatRuntimeContext runtimeContext) {
        try {
            // 清理流：关闭 SSE，注销运行态，释放会话租约。
            // 放在 doFinally 中执行，覆盖成功、失败和客户端断开三种路径。
            runtimeContext.getOutputChannel().tryEmitComplete();
        } finally {
            releaseConversationResources(
                    runtimeContext,
                    runtimeContext.getTaskInfo().conversationId(),
                    runtimeContext.getTaskInfo().leaseKey(),
                    runtimeContext.getTaskInfo().leaseOwnerToken(),
                    "Conversation lease release failed. conversationId={}, leaseKey={}");
        }
    }

    private void releaseConversationResources(
            BusinessChatRuntimeContext runtimeContext,
            String conversationId,
            String leaseKey,
            String leaseOwnerToken,
            String releaseFailedLogMessage) {
        try {
            if (runtimeContext != null) {
                businessChatRuntimeRegistry.unregister(conversationId);
            }
        } finally {
            boolean released = redisLeaseManager.release(leaseKey, leaseOwnerToken);
            if (!released) {
                log.error(releaseFailedLogMessage, conversationId, leaseKey);
            }
        }
    }

    private Flux<ServerSentEvent<BusinessChatStreamEvent>> buildRejectedStream(
            BusinessChatStartPlan startPlan,
            String message) {
        return Flux.just(buildServerSentEvent(BusinessChatStreamEvent.message(
                BusinessChatEventType.TURN_REJECTED.name(),
                startPlan.conversationId(),
                null,
                startPlan.chatMode().getValue(),
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
            // 下游断开后只标记出口关闭；执行链路自己的 STOPPED/FAILED 归档仍由 doFinally 收束。
            runtimeContext.markOutputClosed();
        }
    }

    private ServerSentEvent<BusinessChatStreamEvent> buildServerSentEvent(BusinessChatStreamEvent event) {
        return ServerSentEvent.<BusinessChatStreamEvent>builder()
                .event(event.eventType())
                .data(event)
                .build();
    }

    private Long firstTokenLatency(BusinessChatRuntimeContext runtimeContext) {
        return runtimeContext.getFirstTokenLatencyMs() < 0 ? null : runtimeContext.getFirstTokenLatencyMs();
    }

    private Long firstTokenLatency(BusinessChatFinalizedTurn finalizedTurn) {
        return finalizedTurn.firstTokenLatencyMs() < 0 ? null : finalizedTurn.firstTokenLatencyMs();
    }
}
