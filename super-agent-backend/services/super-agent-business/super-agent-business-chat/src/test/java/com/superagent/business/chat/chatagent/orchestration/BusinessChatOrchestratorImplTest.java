package com.superagent.business.chat.chatagent.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.superagent.business.chat.chatagent.data.BusinessChatExchangeData;
import com.superagent.business.chat.chatagent.data.BusinessChatMemorySummaryData;
import com.superagent.business.chat.chatagent.mapper.BusinessChatExchangeMapper;
import com.superagent.business.chat.chatagent.mapper.BusinessChatMemorySummaryMapper;
import com.superagent.business.chat.chatagent.model.BusinessChatMode;
import com.superagent.business.chat.chatagent.model.BusinessChatModelApiConfigSnapshot;
import com.superagent.business.chat.chatagent.model.BusinessChatModelProvider;
import com.superagent.business.chat.chatagent.runtime.BusinessChatRuntimeContext;
import com.superagent.business.chat.chatagent.model.BusinessChatTaskInfo;
import com.superagent.business.chat.knowledge.graph.KnowledgeGraphClient;
import com.superagent.business.chat.knowledge.model.KnowledgeRouteCandidate;
import com.superagent.business.chat.knowledge.service.KnowledgeManageService;
import com.superagent.business.chat.knowledge.vo.KnowledgeDocumentProfileVo;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
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

    private BusinessChatOrchestratorImpl businessChatOrchestrator;

    @BeforeEach
    void setUp() {
        businessChatOrchestrator = new BusinessChatOrchestratorImpl(
                businessChatMemorySummaryMapper,
                businessChatExchangeMapper,
                knowledgeGraphClient,
                knowledgeManageService);
    }

    @Test
    void shouldBuildOpenEndedPlanWithoutMemory() {
        when(businessChatMemorySummaryMapper.selectOne(any())).thenReturn(null);
        when(businessChatExchangeMapper.selectList(any())).thenReturn(List.of());

        var executionPlan = businessChatOrchestrator.orchestrate(createRuntimeContext(BusinessChatMode.OPEN_ENDED));

        assertThat(executionPlan.originalQuestion()).isEqualTo("请说明这条链路");
        assertThat(executionPlan.rewrittenQuestion()).isEqualTo("请说明这条链路");
        assertThat(executionPlan.historyContextText()).isNull();
        assertThat(executionPlan.memorySummary()).isNull();
        assertThat(executionPlan.recentExchangeCount()).isZero();
        assertThat(executionPlan.freshnessRequirement().required()).isFalse();
        assertThat(executionPlan.knowledgeRoute()).isEqualTo("NOT_REQUIRED");
        assertThat(executionPlan.executionModel()).isEqualTo("qwen-plus");
        assertThat(executionPlan.executionMode()).isEqualTo(BusinessChatMode.OPEN_ENDED);
        assertThat(executionPlan.executionStepList()).contains("加载长期摘要：无", "加载最近对话窗口：0轮", "知识路由：NOT_REQUIRED");
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
        when(knowledgeGraphClient.routeQuestion("结合历史上下文，回答当前问题：请说明这条链路", 5))
                .thenReturn(List.of(new KnowledgeRouteCandidate(
                        9001L,
                        "订单审核手册",
                        "order_scope",
                        "订单知识域",
                        "audit_topic",
                        "审核专题",
                        1.0,
                        1.0,
                        0,
                        List.of("订单审核"),
                        List.of(),
                        "术语命中：订单审核")));

        var executionPlan = businessChatOrchestrator.orchestrate(createRuntimeContext(BusinessChatMode.KNOWLEDGE_BASE));

        assertThat(executionPlan.memorySummary()).isEqualTo("用户之前在讨论订单审核链路。");
        assertThat(executionPlan.historyContextText()).contains(
                "长期摘要",
                "用户之前在讨论订单审核链路。",
                "最近对话",
                "订单审核链路有哪些节点？",
                "包括提交、风控、人工审核和归档。");
        assertThat(executionPlan.recentExchangeCount()).isEqualTo(1);
        assertThat(executionPlan.rewrittenQuestion()).isEqualTo("结合历史上下文，回答当前问题：请说明这条链路");
        assertThat(executionPlan.knowledgeRoute()).isEqualTo("KNOWLEDGE_BASE|DOCUMENT_MATCHED");
        assertThat(executionPlan.knowledgeRouteCandidateList()).hasSize(1);
        assertThat(executionPlan.executionMode()).isEqualTo(BusinessChatMode.KNOWLEDGE_BASE);
        assertThat(executionPlan.executionStepList()).contains(
                "加载长期摘要：已加载",
                "加载最近对话窗口：1轮",
                "知识路由：KNOWLEDGE_BASE|DOCUMENT_MATCHED",
                "路由候选文档：1");
    }

    @Test
    void shouldBuildCurrentDocumentPlanWithSelectedDocumentContext() {
        when(businessChatMemorySummaryMapper.selectOne(any())).thenReturn(null);
        when(businessChatExchangeMapper.selectList(any())).thenReturn(List.of());
        KnowledgeDocumentProfileVo profileVo = new KnowledgeDocumentProfileVo();
        profileVo.setDocumentId("9001");
        profileVo.setSummaryText("这是一份订单审核规范。");
        profileVo.setTerms(List.of("订单审核", "风控"));
        profileVo.setAnswerableQuestions(List.of("订单审核有哪些节点？"));
        when(knowledgeManageService.queryDocumentProfile(any())).thenReturn(profileVo);
        when(knowledgeManageService.queryDocumentParsedText(any())).thenReturn("正文说明：订单审核包括提交、风控、人工审核和归档。");

        var executionPlan = businessChatOrchestrator.orchestrate(
                createRuntimeContext(BusinessChatMode.CURRENT_DOCUMENT, "订单审核有哪些节点？"));

        assertThat(executionPlan.knowledgeRoute()).isEqualTo("CURRENT_DOCUMENT");
        assertThat(executionPlan.selectedDocumentContextText()).contains(
                "文档ID：9001",
                "文档名称：订单审核规范.pdf",
                "画像摘要：这是一份订单审核规范。",
                "可回答问题：订单审核有哪些节点？",
                "术语：订单审核、风控",
                "文档正文：",
                "订单审核包括提交、风控、人工审核和归档。");
        assertThat(executionPlan.executionStepList()).contains("当前文档上下文：已加载");
    }

    @Test
    void shouldMarkFreshnessRequiredForLatestExternalQuestion() {
        when(businessChatMemorySummaryMapper.selectOne(any())).thenReturn(null);
        when(businessChatExchangeMapper.selectList(any())).thenReturn(List.of());

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

        var executionPlan = businessChatOrchestrator.orchestrate(
                createRuntimeContext(BusinessChatMode.OPEN_ENDED, "最近我们聊了什么？"));

        assertThat(executionPlan.freshnessRequirement().required()).isFalse();
    }

    @Test
    void shouldUseSelectedModelConfigAsExecutionModel() {
        when(businessChatMemorySummaryMapper.selectOne(any())).thenReturn(null);
        when(businessChatExchangeMapper.selectList(any())).thenReturn(List.of());

        var executionPlan = businessChatOrchestrator.orchestrate(
                createRuntimeContext(BusinessChatMode.OPEN_ENDED, "请说明这条链路", "deepseek-v4-flash"));

        assertThat(executionPlan.executionModel()).isEqualTo("deepseek-v4-flash");
        assertThat(executionPlan.executionStepList()).contains("执行模型：deepseek-v4-flash");
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
                        "api-key"),
                chatMode == BusinessChatMode.CURRENT_DOCUMENT ? 9001L : null,
                chatMode == BusinessChatMode.CURRENT_DOCUMENT ? "订单审核规范.pdf" : null,
                "trace-1",
                "chat:conversation:running:conversation-1",
                "owner-1",
                Duration.ofSeconds(30),
                System.currentTimeMillis());
        return new BusinessChatRuntimeContext(taskInfo, Sinks.many().unicast().onBackpressureBuffer());
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
