package com.superagent.business.chat.chatagent.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.inOrder;

import com.superagent.business.chat.chatagent.execution.agent.BusinessChatAgentType;
import com.superagent.business.chat.chatagent.config.BusinessChatClarificationProperties;
import com.superagent.business.chat.chatagent.config.BusinessChatHistorySummaryProperties;
import com.superagent.business.chat.chatagent.persistence.data.BusinessChatExchangeData;
import com.superagent.business.chat.chatagent.persistence.data.BusinessChatMemorySummaryData;
import com.superagent.business.chat.chatagent.persistence.mapper.BusinessChatExchangeMapper;
import com.superagent.business.chat.chatagent.persistence.mapper.BusinessChatMemorySummaryMapper;
import com.superagent.business.chat.chatagent.orchestration.model.BusinessChatMode;
import com.superagent.business.chat.chatagent.execution.model.BusinessChatModelApiConfigSnapshot;
import com.superagent.business.chat.chatagent.execution.model.BusinessChatModelProvider;
import com.superagent.business.chat.chatagent.persistence.BusinessChatPersistenceService;
import com.superagent.business.chat.chatagent.runtime.BusinessChatRuntimeContext;
import com.superagent.business.chat.chatagent.runtime.BusinessChatTaskInfo;
import com.superagent.business.chat.chatagent.trace.BusinessChatTraceStageRunner;
import com.superagent.business.chat.knowledge.route.config.KnowledgeRouteProperties;
import com.superagent.business.chat.knowledge.route.graph.KnowledgeGraphClient;
import com.superagent.business.chat.knowledge.route.messaging.KnowledgeShadowRouteProducer;
import com.superagent.business.chat.knowledge.route.model.KnowledgeRouteCandidate;
import com.superagent.business.chat.knowledge.route.model.KnowledgeRouteDecision;
import com.superagent.business.chat.knowledge.document.service.KnowledgeManageService;
import com.superagent.business.chat.knowledge.route.service.KnowledgeRouteTraceService;
import com.superagent.business.chat.knowledge.retrieval.KnowledgeRetrievalResult;
import com.superagent.business.chat.knowledge.retrieval.KnowledgeRetrievalParentEvidence;
import com.superagent.business.chat.knowledge.retrieval.KnowledgeRetrievalService;
import com.superagent.business.chat.knowledge.api.vo.KnowledgeDocumentProfileVo;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Sinks;

@ExtendWith(MockitoExtension.class)
class BusinessChatOrchestratorImplTest {

    @Mock
    private BusinessChatMemorySummaryMapper businessChatMemorySummaryMapper;

    @Mock
    private BusinessChatExchangeMapper businessChatExchangeMapper;

    @Mock
    private KnowledgeGraphClient knowledgeGraphClient;

    @Mock
    private KnowledgeManageService knowledgeManageService;

    @Mock
    private KnowledgeRouteTraceService knowledgeRouteTraceService;

    @Mock
    private KnowledgeShadowRouteProducer shadowRouteProducer;

    @Mock
    private BusinessChatQuestionRewriteService questionRewriteService;

    @Mock
    private KnowledgeRetrievalService knowledgeRetrievalService;

    @Mock
    private BusinessChatPersistenceService businessChatPersistenceService;

    private BusinessChatHistorySummaryProperties historySummaryProperties;

    private BusinessChatClarificationProperties clarificationProperties;

    private KnowledgeRouteProperties routeProperties;

    private BusinessChatOrchestratorImpl businessChatOrchestrator;

    @BeforeEach
    void setUp() {
        historySummaryProperties = buildHistorySummaryProperties(true, 4, 2200, 1400);
        clarificationProperties = buildClarificationProperties();
        routeProperties = buildRouteProperties();
        BusinessChatTraceStageRunner traceStageRunner = new BusinessChatTraceStageRunner(businessChatPersistenceService);
        businessChatOrchestrator = new BusinessChatOrchestratorImpl(
                businessChatMemorySummaryMapper,
                businessChatExchangeMapper,
                knowledgeGraphClient,
                knowledgeManageService,
                knowledgeRouteTraceService,
                shadowRouteProducer,
                historySummaryProperties,
                clarificationProperties,
                routeProperties,
                questionRewriteService,
                knowledgeRetrievalService,
                traceStageRunner);
        lenient().when(knowledgeRetrievalService.retrieve(any())).thenReturn(KnowledgeRetrievalResult.empty());
    }

    @Test
    void shouldBuildOpenEndedPlanWithoutMemory() {
        when(businessChatMemorySummaryMapper.selectOne(any())).thenReturn(null);
        when(businessChatExchangeMapper.selectList(any())).thenReturn(List.of());
        when(questionRewriteService.rewrite(any(), any(), any(), any())).thenReturn("请说明这条链路");

        var executionPlan = businessChatOrchestrator.orchestrate(createRuntimeContext(BusinessChatMode.OPEN_ENDED));

        assertThat(executionPlan.originalQuestion()).isEqualTo("请说明这条链路");
        assertThat(executionPlan.rewrittenQuestion()).isEqualTo("请说明这条链路");
        assertThat(executionPlan.rewriteHistoryContextText()).isNull();
        assertThat(executionPlan.answerHistoryContextText()).isNull();
        assertThat(executionPlan.memorySummary()).isNull();
        assertThat(executionPlan.recentExchangeCount()).isZero();
        assertThat(executionPlan.freshnessRequirement().required()).isFalse();
        assertThat(executionPlan.knowledgeRoute()).isEqualTo("NOT_REQUIRED");
        assertThat(executionPlan.executionModel()).isEqualTo("qwen-plus");
        assertThat(executionPlan.executionMode()).isEqualTo(BusinessChatMode.OPEN_ENDED);
        assertThat(executionPlan.executionStepList()).contains("加载长期摘要：无", "加载最近对话窗口：0轮", "知识路由：NOT_REQUIRED");
    }

    @Test
    void shouldRecordOrchestrationTraceStagesInExecutionOrder() {
        when(businessChatMemorySummaryMapper.selectOne(any())).thenReturn(null);
        when(businessChatExchangeMapper.selectList(any())).thenReturn(List.of());
        when(questionRewriteService.rewrite(any(), any(), any(), any())).thenReturn("请说明这条链路");

        businessChatOrchestrator.orchestrate(createRuntimeContext(BusinessChatMode.OPEN_ENDED));

        InOrder inOrder = inOrder(businessChatPersistenceService);
        inOrder.verify(businessChatPersistenceService).startTraceStage(any(), org.mockito.ArgumentMatchers.eq("MEMORY_LOAD"), org.mockito.ArgumentMatchers.eq("会话记忆加载"), org.mockito.ArgumentMatchers.eq(100));
        inOrder.verify(businessChatPersistenceService).completeTraceStage(any(), org.mockito.ArgumentMatchers.eq("会话记忆加载完成，最近对话窗口 0 轮"), any());
        inOrder.verify(businessChatPersistenceService).startTraceStage(any(), org.mockito.ArgumentMatchers.eq("QUESTION_REWRITE"), org.mockito.ArgumentMatchers.eq("问题改写"), org.mockito.ArgumentMatchers.eq(200));
        inOrder.verify(businessChatPersistenceService).completeTraceStage(any(), org.mockito.ArgumentMatchers.eq("问题改写完成"), any());
        inOrder.verify(businessChatPersistenceService).startTraceStage(any(), org.mockito.ArgumentMatchers.eq("FRESHNESS_DETECTION"), org.mockito.ArgumentMatchers.eq("时效性判断"), org.mockito.ArgumentMatchers.eq(250));
        inOrder.verify(businessChatPersistenceService).completeTraceStage(any(), org.mockito.ArgumentMatchers.eq("未命中实时信息信号"), any());
        inOrder.verify(businessChatPersistenceService).startTraceStage(any(), org.mockito.ArgumentMatchers.eq("ROUTE_DECISION"), org.mockito.ArgumentMatchers.eq("路由判定"), org.mockito.ArgumentMatchers.eq(300));
        inOrder.verify(businessChatPersistenceService).completeTraceStage(any(), org.mockito.ArgumentMatchers.eq("路由判定完成，本轮模式为 OPEN_ENDED"), any());
        inOrder.verify(businessChatPersistenceService).startTraceStage(any(), org.mockito.ArgumentMatchers.eq("GRAPH_QUERY"), org.mockito.ArgumentMatchers.eq("结构图查询"), org.mockito.ArgumentMatchers.eq(400));
        inOrder.verify(businessChatPersistenceService).completeTraceStage(any(), org.mockito.ArgumentMatchers.eq("开放问答模式不查询结构图"), any());
        inOrder.verify(businessChatPersistenceService).startTraceStage(any(), org.mockito.ArgumentMatchers.eq("DOCUMENT_CONTEXT"), org.mockito.ArgumentMatchers.eq("文档画像加载"), org.mockito.ArgumentMatchers.eq(450));
        inOrder.verify(businessChatPersistenceService).completeTraceStage(any(), org.mockito.ArgumentMatchers.eq("当前模式不需要文档画像"), any());
        inOrder.verify(businessChatPersistenceService).startTraceStage(any(), org.mockito.ArgumentMatchers.eq("EVIDENCE_RETRIEVAL"), org.mockito.ArgumentMatchers.eq("证据检索"), org.mockito.ArgumentMatchers.eq(500));
        inOrder.verify(businessChatPersistenceService).completeTraceStage(any(), org.mockito.ArgumentMatchers.eq("没有检索到可注入回答的正文证据"), any());
    }

    @Test
    void shouldRecordGraphQueryFailureWhenKnowledgeGraphThrows() {
        when(businessChatMemorySummaryMapper.selectOne(any())).thenReturn(null);
        when(businessChatExchangeMapper.selectList(any())).thenReturn(List.of());
        when(questionRewriteService.rewrite(any(), any(), any(), any())).thenReturn("订单审核怎么配置？");
        when(knowledgeGraphClient.routeQuestion("订单审核怎么配置？", 5))
                .thenThrow(new IllegalStateException("graph query failed"));

        assertThatThrownBy(() -> businessChatOrchestrator.orchestrate(
                createRuntimeContext(BusinessChatMode.KNOWLEDGE_BASE, "订单审核怎么配置？")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("graph query failed");

        verify(businessChatPersistenceService).startTraceStage(
                any(),
                org.mockito.ArgumentMatchers.eq("GRAPH_QUERY"),
                org.mockito.ArgumentMatchers.eq("结构图查询"),
                org.mockito.ArgumentMatchers.eq(400));
        verify(businessChatPersistenceService).failTraceStage(
                any(),
                org.mockito.ArgumentMatchers.isA(IllegalStateException.class));
    }

    @Test
    void shouldBuildKnowledgePlanWithLoadedMemoryAndRewrittenQuestion() {
        BusinessChatMemorySummaryData summaryData = new BusinessChatMemorySummaryData();
        summaryData.setDialogueCode("conversation-1");
        summaryData.setSummaryText("用户之前在讨论订单审核链路。");
        when(businessChatMemorySummaryMapper.selectOne(any())).thenReturn(summaryData);
        BusinessChatExchangeData latestExchangeData = buildExchange(
                2000L,
                "订单审核链路有哪些节点？",
                "包括提交、风控、人工审核和归档。",
                LocalDateTime.of(2026, 4, 24, 9, 0));
        when(businessChatExchangeMapper.selectList(any())).thenReturn(List.of(latestExchangeData));
        when(questionRewriteService.rewrite(any(), any(), any(), any())).thenReturn("订单审核链路有哪些风险？");
        when(knowledgeGraphClient.routeQuestion("订单审核链路有哪些风险？", 5))
                .thenReturn(routeDecision(List.of(new KnowledgeRouteCandidate(
                        9001L,
                        "订单审核手册",
                        "order_scope",
                        "订单知识域",
                        "audit_topic",
                        "审核专题",
                        1.0,
                        1.0,
                        1.0,
                        1.0,
                        0,
                        List.of("订单审核"),
                        List.of(),
                        "术语命中：订单审核"))));

        var executionPlan = businessChatOrchestrator.orchestrate(
                createRuntimeContext(BusinessChatMode.KNOWLEDGE_BASE, "这个有哪些风险？"));

        assertThat(executionPlan.memorySummary()).isEqualTo("用户之前在讨论订单审核链路。");
        assertThat(executionPlan.rewriteHistoryContextText()).contains(
                "长期摘要",
                "用户之前在讨论订单审核链路。",
                "最近对话",
                "订单审核链路有哪些节点？");
        assertThat(executionPlan.rewriteHistoryContextText()).doesNotContain("包括提交、风控、人工审核和归档。");
        assertThat(executionPlan.answerHistoryContextText()).contains(
                "长期摘要",
                "用户之前在讨论订单审核链路。",
                "最近对话",
                "订单审核链路有哪些节点？",
                "包括提交、风控、人工审核和归档。");
        assertThat(executionPlan.recentExchangeCount()).isEqualTo(1);
        assertThat(executionPlan.rewrittenQuestion()).isEqualTo("订单审核链路有哪些风险？");
        assertThat(executionPlan.knowledgeRoute()).isEqualTo("KNOWLEDGE_BASE|DOCUMENT_MATCHED");
        assertThat(executionPlan.knowledgeRouteCandidateList()).hasSize(1);
        assertThat(executionPlan.clarificationPlan().required()).isFalse();
        assertThat(executionPlan.answerAgentStep().agentType()).isEqualTo(BusinessChatAgentType.KNOWLEDGE_QA);
        assertThat(executionPlan.executionMode()).isEqualTo(BusinessChatMode.KNOWLEDGE_BASE);
        verify(knowledgeGraphClient).routeQuestion("订单审核链路有哪些风险？", 5);
        assertThat(executionPlan.executionStepList()).contains(
                "加载长期摘要：已加载",
                "加载最近对话窗口：1轮",
                "知识路由：KNOWLEDGE_BASE|DOCUMENT_MATCHED",
                "路由候选文档：1");
    }

    @Test
    void shouldBuildClarificationPlanWhenKnowledgeRouteHasNoCandidates() {
        when(businessChatMemorySummaryMapper.selectOne(any())).thenReturn(null);
        when(businessChatExchangeMapper.selectList(any())).thenReturn(List.of());
        when(questionRewriteService.rewrite(any(), any(), any(), any())).thenReturn("订单审核怎么配置？");
        when(knowledgeGraphClient.routeQuestion("订单审核怎么配置？", 5)).thenReturn(KnowledgeRouteDecision.empty());

        var executionPlan = businessChatOrchestrator.orchestrate(
                createRuntimeContext(BusinessChatMode.KNOWLEDGE_BASE, "订单审核怎么配置？"));

        assertThat(executionPlan.answerAgentStep().agentType()).isEqualTo(BusinessChatAgentType.CLARIFICATION);
        assertThat(executionPlan.intentLabel()).isEqualTo("knowledge_route_clarification");
        assertThat(executionPlan.knowledgeRoute()).isEqualTo("KNOWLEDGE_BASE|NO_DOCUMENT_MATCH|CLARIFICATION_REQUIRED");
        assertThat(executionPlan.clarificationPlan().required()).isTrue();
        assertThat(executionPlan.clarificationPlan().reason()).isEqualTo("知识路由没有召回候选文档");
        assertThat(executionPlan.clarificationPlan().reply()).contains("当前没有匹配到稳定的候选文档");
        assertThat(executionPlan.clarificationPlan().optionList()).isEmpty();
    }

    @Test
    void shouldBuildClarificationPlanWhenCrossScopeCandidatesHaveSmallScoreGap() {
        when(businessChatMemorySummaryMapper.selectOne(any())).thenReturn(null);
        when(businessChatExchangeMapper.selectList(any())).thenReturn(List.of());
        when(questionRewriteService.rewrite(any(), any(), any(), any())).thenReturn("流程怎么配置？");
        when(knowledgeGraphClient.routeQuestion("流程怎么配置？", 5)).thenReturn(routeDecision(List.of(
                routeCandidate(9001L, "订单审核手册", "order_scope", "订单知识域", 5.0),
                routeCandidate(9002L, "合同审批手册", "contract_scope", "合同知识域", 4.4),
                routeCandidate(9003L, "费用报销手册", "expense_scope", "费用知识域", 3.0))));

        var executionPlan = businessChatOrchestrator.orchestrate(
                createRuntimeContext(BusinessChatMode.KNOWLEDGE_BASE, "流程怎么配置？"));

        assertThat(executionPlan.answerAgentStep().agentType()).isEqualTo(BusinessChatAgentType.CLARIFICATION);
        assertThat(executionPlan.knowledgeRoute()).isEqualTo("KNOWLEDGE_BASE|DOCUMENT_MATCHED|CLARIFICATION_REQUIRED");
        assertThat(executionPlan.clarificationPlan().required()).isTrue();
        assertThat(executionPlan.clarificationPlan().reason()).contains("Top1/Top2 跨知识域且分差过小");
        assertThat(executionPlan.clarificationPlan().reply()).contains(
                "这个问题目前存在文档范围歧义",
                "1. 《订单审核手册》",
                "2. 《合同审批手册》",
                "3. 《费用报销手册》");
        assertThat(executionPlan.clarificationPlan().optionList()).hasSize(3);
    }

    @Test
    void shouldNotClarifyWhenCloseCandidatesBelongToSameScope() {
        when(businessChatMemorySummaryMapper.selectOne(any())).thenReturn(null);
        when(businessChatExchangeMapper.selectList(any())).thenReturn(List.of());
        when(questionRewriteService.rewrite(any(), any(), any(), any())).thenReturn("订单审核流程怎么配置？");
        when(knowledgeGraphClient.routeQuestion("订单审核流程怎么配置？", 5)).thenReturn(routeDecision(List.of(
                routeCandidate(9001L, "订单审核手册", "order_scope", "订单知识域", 5.0),
                routeCandidate(9002L, "订单风控手册", "order_scope", "订单知识域", 4.4))));

        var executionPlan = businessChatOrchestrator.orchestrate(
                createRuntimeContext(BusinessChatMode.KNOWLEDGE_BASE, "订单审核流程怎么配置？"));

        assertThat(executionPlan.answerAgentStep().agentType()).isEqualTo(BusinessChatAgentType.KNOWLEDGE_QA);
        assertThat(executionPlan.clarificationPlan().required()).isFalse();
        assertThat(executionPlan.knowledgeRoute()).isEqualTo("KNOWLEDGE_BASE|DOCUMENT_MATCHED");
    }

    @Test
    void shouldSkipHistoryLoadingWhenDisabled() {
        historySummaryProperties.setEnabled(false);
        when(questionRewriteService.rewrite(any(), any(), any(), any())).thenReturn("这个怎么配置？");

        var executionPlan = businessChatOrchestrator.orchestrate(
                createRuntimeContext(BusinessChatMode.OPEN_ENDED, "这个怎么配置？"));

        assertThat(executionPlan.rewrittenQuestion()).isEqualTo("这个怎么配置？");
        assertThat(executionPlan.rewriteHistoryContextText()).isNull();
        assertThat(executionPlan.answerHistoryContextText()).isNull();
        assertThat(executionPlan.recentExchangeCount()).isZero();
        verify(businessChatMemorySummaryMapper, never()).selectOne(any());
        verify(businessChatExchangeMapper, never()).selectList(any());
    }

    @Test
    void shouldLimitHistoryTextByConfiguredBounds() {
        historySummaryProperties.setKeepRecentTurns(1);
        historySummaryProperties.setSummaryMaxChars(8);
        historySummaryProperties.setRecentTranscriptMaxChars(80);
        BusinessChatMemorySummaryData summaryData = new BusinessChatMemorySummaryData();
        summaryData.setDialogueCode("conversation-1");
        summaryData.setSummaryText("长期摘要内容超过限制");
        when(businessChatMemorySummaryMapper.selectOne(any())).thenReturn(summaryData);
        when(businessChatExchangeMapper.selectList(any())).thenReturn(List.of(
                buildExchange(
                        2000L,
                        "第一个问题",
                        "第一个回答",
                        LocalDateTime.of(2026, 4, 24, 9, 0)),
                buildExchange(
                        2001L,
                        "第二个问题",
                        "第二个回答",
                        LocalDateTime.of(2026, 4, 24, 10, 0))));
        when(questionRewriteService.rewrite(any(), any(), any(), any())).thenReturn("这个怎么配置？");

        var executionPlan = businessChatOrchestrator.orchestrate(
                createRuntimeContext(BusinessChatMode.OPEN_ENDED, "这个怎么配置？"));

        assertThat(executionPlan.memorySummary()).isEqualTo("长期摘要内容超过");
        assertThat(executionPlan.recentExchangeCount()).isEqualTo(1);
        assertThat(executionPlan.answerHistoryContextText()).hasSizeLessThanOrEqualTo(80);
        assertThat(executionPlan.answerHistoryContextText()).contains("第二个问题");
        assertThat(executionPlan.answerHistoryContextText()).doesNotContain("第一个问题");
    }

    @Test
    void shouldBuildCurrentDocumentPlanWithSelectedDocumentContext() {
        when(businessChatMemorySummaryMapper.selectOne(any())).thenReturn(null);
        when(businessChatExchangeMapper.selectList(any())).thenReturn(List.of());
        when(questionRewriteService.rewrite(any(), any(), any(), any())).thenReturn("订单审核有哪些节点？");
        KnowledgeDocumentProfileVo profileVo = new KnowledgeDocumentProfileVo();
        profileVo.setDocumentId("9001");
        profileVo.setSummaryText("这是一份订单审核规范。");
        profileVo.setTerms(List.of("订单审核", "风控"));
        profileVo.setAnswerableQuestions(List.of("订单审核有哪些节点？"));
        when(knowledgeManageService.queryDocumentProfile(any())).thenReturn(profileVo);
        when(knowledgeRetrievalService.retrieve(any())).thenReturn(
                new KnowledgeRetrievalResult(
                        "[1]\nParent正文：\n订单审核包括提交、风控、人工审核和归档。",
                        List.of(parentEvidence())));

        var executionPlan = businessChatOrchestrator.orchestrate(
                createRuntimeContext(BusinessChatMode.CURRENT_DOCUMENT, "订单审核有哪些节点？"));

        assertThat(executionPlan.knowledgeRoute()).isEqualTo("CURRENT_DOCUMENT");
        assertThat(executionPlan.selectedDocumentContextText()).contains(
                "文档ID：9001",
                "文档名称：订单审核规范.pdf",
                "画像摘要：这是一份订单审核规范。",
                "可回答问题：订单审核有哪些节点？",
                "术语：订单审核、风控");
        assertThat(executionPlan.retrievalEvidenceContextText()).contains(
                "Parent正文：",
                "订单审核包括提交、风控、人工审核和归档。");
        assertThat(executionPlan.shortCircuit()).isFalse();
        assertThat(executionPlan.executionStepList()).contains("当前文档画像上下文：已加载");
        verify(knowledgeGraphClient, never()).routeQuestion(any(), anyInt());
        verify(knowledgeManageService, never()).queryDocumentParsedText(any());
        verify(knowledgeRetrievalService).retrieve(any());
        verify(shadowRouteProducer).publish(any());
    }

    @Test
    void shouldShortCircuitCurrentDocumentWhenRetrievalHasNoEvidence() {
        when(businessChatMemorySummaryMapper.selectOne(any())).thenReturn(null);
        when(businessChatExchangeMapper.selectList(any())).thenReturn(List.of());
        when(questionRewriteService.rewrite(any(), any(), any(), any())).thenReturn("订单审核有哪些节点？");
        KnowledgeDocumentProfileVo profileVo = new KnowledgeDocumentProfileVo();
        profileVo.setDocumentId("9001");
        profileVo.setSummaryText("这是一份订单审核规范。");
        when(knowledgeManageService.queryDocumentProfile(any())).thenReturn(profileVo);
        when(knowledgeRetrievalService.retrieve(any())).thenReturn(KnowledgeRetrievalResult.empty());

        var executionPlan = businessChatOrchestrator.orchestrate(
                createRuntimeContext(BusinessChatMode.CURRENT_DOCUMENT, "订单审核有哪些节点？"));

        assertThat(executionPlan.shortCircuit()).isTrue();
        assertThat(executionPlan.shortCircuitReply())
                .isEqualTo("当前文档中没有检索到足够证据，无法基于当前文档回答该问题。");
        assertThat(executionPlan.intentLabel()).isEqualTo("evidence_short_circuit");
        assertThat(executionPlan.executionStepList()).contains("证据短路：未调用模型生成");
    }

    @Test
    void shouldShortCircuitKnowledgeBaseWhenRouteMatchedButRetrievalHasNoEvidence() {
        when(businessChatMemorySummaryMapper.selectOne(any())).thenReturn(null);
        when(businessChatExchangeMapper.selectList(any())).thenReturn(List.of());
        when(questionRewriteService.rewrite(any(), any(), any(), any())).thenReturn("订单审核有哪些节点？");
        when(knowledgeGraphClient.routeQuestion("订单审核有哪些节点？", 5))
                .thenReturn(routeDecision(List.of(routeCandidate(9001L, "订单审核手册", "order_scope", "订单知识域", 5.0))));
        when(knowledgeRetrievalService.retrieve(any())).thenReturn(KnowledgeRetrievalResult.empty());

        var executionPlan = businessChatOrchestrator.orchestrate(
                createRuntimeContext(BusinessChatMode.KNOWLEDGE_BASE, "订单审核有哪些节点？"));

        assertThat(executionPlan.shortCircuit()).isTrue();
        assertThat(executionPlan.shortCircuitReply())
                .isEqualTo("知识库中没有检索到足够证据，无法基于知识库回答该问题。");
        assertThat(executionPlan.answerAgentStep().agentType()).isEqualTo(BusinessChatAgentType.KNOWLEDGE_QA);
        assertThat(executionPlan.executionStepList()).contains("证据短路：未调用模型生成");
    }

    @Test
    void shouldMarkFreshnessRequiredForLatestExternalQuestion() {
        when(businessChatMemorySummaryMapper.selectOne(any())).thenReturn(null);
        when(businessChatExchangeMapper.selectList(any())).thenReturn(List.of());
        when(questionRewriteService.rewrite(any(), any(), any(), any())).thenReturn("今天最新政策是什么？");

        var executionPlan = businessChatOrchestrator.orchestrate(
                createRuntimeContext(BusinessChatMode.OPEN_ENDED, "今天最新政策是什么？"));

        assertThat(executionPlan.freshnessRequirement().required()).isTrue();
        assertThat(executionPlan.freshnessRequirement().capability()).isEqualTo("UNAVAILABLE");
        assertThat(executionPlan.freshnessRequirement().matchedSignalList()).contains("今天", "最新");
        assertThat(executionPlan.knowledgeRoute()).isEqualTo("NOT_REQUIRED|FRESHNESS_REQUIRED");
        assertThat(executionPlan.executionModel()).isEqualTo("qwen-plus");
    }

    @Test
    void shouldNotMarkConversationMemoryQuestionAsFreshnessRequired() {
        when(businessChatMemorySummaryMapper.selectOne(any())).thenReturn(null);
        when(businessChatExchangeMapper.selectList(any())).thenReturn(List.of());
        when(questionRewriteService.rewrite(any(), any(), any(), any())).thenReturn("最近我们聊了什么？");

        var executionPlan = businessChatOrchestrator.orchestrate(
                createRuntimeContext(BusinessChatMode.OPEN_ENDED, "最近我们聊了什么？"));

        assertThat(executionPlan.freshnessRequirement().required()).isFalse();
    }

    @Test
    void shouldUseSelectedModelConfigAsExecutionModel() {
        when(businessChatMemorySummaryMapper.selectOne(any())).thenReturn(null);
        when(businessChatExchangeMapper.selectList(any())).thenReturn(List.of());
        when(questionRewriteService.rewrite(any(), any(), any(), any())).thenReturn("请说明这条链路");

        var executionPlan = businessChatOrchestrator.orchestrate(
                createRuntimeContext(BusinessChatMode.OPEN_ENDED, "请说明这条链路", "deepseek-v4-flash"));

        assertThat(executionPlan.executionModel()).isEqualTo("deepseek-v4-flash");
        assertThat(executionPlan.executionStepList()).contains("执行模型：deepseek-v4-flash");
    }

    @Test
    void shouldStopBeforeKnowledgeRouteWhenRewriteFails() {
        BusinessChatMemorySummaryData summaryData = new BusinessChatMemorySummaryData();
        summaryData.setDialogueCode("conversation-1");
        summaryData.setSummaryText("用户之前在讨论订单审核链路。");
        when(businessChatMemorySummaryMapper.selectOne(any())).thenReturn(summaryData);
        when(businessChatExchangeMapper.selectList(any())).thenReturn(List.of(buildExchange(
                2000L,
                "订单审核链路有哪些节点？",
                "包括提交、风控、人工审核和归档。",
                LocalDateTime.of(2026, 4, 24, 9, 0))));
        when(questionRewriteService.rewrite(any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("question rewrite model response must be a JSON object."));

        assertThatThrownBy(() -> businessChatOrchestrator.orchestrate(
                createRuntimeContext(BusinessChatMode.KNOWLEDGE_BASE, "这个有哪些风险？")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("question rewrite model response must be a JSON object");
        verify(knowledgeGraphClient, never()).routeQuestion(any(), anyInt());
    }

    private BusinessChatRuntimeContext createRuntimeContext(BusinessChatMode chatMode) {
        return createRuntimeContext(chatMode, "请说明这条链路");
    }

    private BusinessChatRuntimeContext createRuntimeContext(BusinessChatMode chatMode, String question) {
        return createRuntimeContext(chatMode, question, "qwen-plus");
    }

    private BusinessChatRuntimeContext createRuntimeContext(BusinessChatMode chatMode, String question, String modelName) {
        BusinessChatTaskInfo taskInfo = new BusinessChatTaskInfo(
                1001L,
                2001L,
                question,
                "conversation-1",
                chatMode,
                new BusinessChatModelApiConfigSnapshot(
                        3001L,
                        BusinessChatModelProvider.DASHSCOPE,
                        "DASHSCOPE",
                        "https://dashscope.aliyuncs.com/compatible-mode",
                        modelName,
                        "api-key", java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, 1000, "CNY"),
                chatMode == BusinessChatMode.CURRENT_DOCUMENT ? 9001L : null,
                chatMode == BusinessChatMode.CURRENT_DOCUMENT ? "订单审核规范.pdf" : null,
                "trace-1",
                "chat:conversation:running:conversation-1",
                "owner-1",
                Duration.ofSeconds(30),
                System.currentTimeMillis());
        return new BusinessChatRuntimeContext(taskInfo, Sinks.many().unicast().onBackpressureBuffer());
    }

    private BusinessChatHistorySummaryProperties buildHistorySummaryProperties(
            boolean enabled,
            int keepRecentTurns,
            int recentTranscriptMaxChars,
            int summaryMaxChars) {
        BusinessChatHistorySummaryProperties properties = new BusinessChatHistorySummaryProperties();
        properties.setEnabled(enabled);
        properties.setKeepRecentTurns(keepRecentTurns);
        properties.setCompressionBatchTurns(6);
        properties.setRecentTranscriptMaxChars(recentTranscriptMaxChars);
        properties.setSummaryMaxChars(summaryMaxChars);
        return properties;
    }

    private BusinessChatClarificationProperties buildClarificationProperties() {
        BusinessChatClarificationProperties properties = new BusinessChatClarificationProperties();
        properties.setEnabled(true);
        properties.setMaxOptions(3);
        properties.setMinTopScore(1.0D);
        properties.setAmbiguousScoreGap(0.8D);
        return properties;
    }

    private KnowledgeRouteProperties buildRouteProperties() {
        KnowledgeRouteProperties properties = new KnowledgeRouteProperties();
        properties.setSuccessConfidence(0.05D);
        properties.setLexicalWeight(1.0D);
        properties.setSemanticWeight(1.6D);
        return properties;
    }

    private KnowledgeRouteCandidate routeCandidate(
            long documentId,
            String documentName,
            String scopeCode,
            String scopeName,
            double score) {
        return new KnowledgeRouteCandidate(
                documentId,
                documentName,
                scopeCode,
                scopeName,
                "topic",
                "专题",
                score,
                score,
                score,
                score,
                0,
                List.of(documentName),
                List.of(),
                "术语命中：" + documentName);
    }

    private KnowledgeRouteDecision routeDecision(List<KnowledgeRouteCandidate> candidates) {
        return new KnowledgeRouteDecision(List.of(), List.of(), candidates);
    }

    private KnowledgeRetrievalParentEvidence parentEvidence() {
        return new KnowledgeRetrievalParentEvidence(
                8001L,
                9001L,
                "订单审核规范.pdf",
                "第一章",
                "订单审核包括提交、风控、人工审核和归档。",
                1.0D,
                List.of(7001L),
                List.of("VECTOR"));
    }

    private BusinessChatExchangeData buildExchange(
            Long exchangeId,
            String userPrompt,
            String replyContent,
            LocalDateTime createTime) {
        BusinessChatExchangeData exchangeData = new BusinessChatExchangeData();
        exchangeData.setId(exchangeId);
        exchangeData.setUserPrompt(userPrompt);
        exchangeData.setReplyContent(replyContent);
        exchangeData.setCreateTime(createTime);
        return exchangeData;
    }
}
