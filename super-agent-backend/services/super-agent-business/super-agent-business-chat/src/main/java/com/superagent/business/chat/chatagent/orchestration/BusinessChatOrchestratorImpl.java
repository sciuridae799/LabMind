package com.superagent.business.chat.chatagent.orchestration;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.superagent.business.chat.chatagent.agent.BusinessChatAgentType;
import com.superagent.business.chat.chatagent.data.BusinessChatExchangeData;
import com.superagent.business.chat.chatagent.data.BusinessChatMemorySummaryData;
import com.superagent.business.chat.chatagent.mapper.BusinessChatExchangeMapper;
import com.superagent.business.chat.chatagent.mapper.BusinessChatMemorySummaryMapper;
import com.superagent.business.chat.chatagent.model.BusinessChatExecutionPlan;
import com.superagent.business.chat.chatagent.model.BusinessChatExchangeState;
import com.superagent.business.chat.chatagent.model.BusinessChatFreshnessRequirement;
import com.superagent.business.chat.chatagent.model.BusinessChatHistoryContext;
import com.superagent.business.chat.chatagent.model.BusinessChatMode;
import com.superagent.business.chat.chatagent.model.BusinessChatRecentExchange;
import com.superagent.business.chat.chatagent.runtime.BusinessChatRuntimeContext;
import com.superagent.business.chat.knowledge.dto.KnowledgeDocumentIdRequest;
import com.superagent.business.chat.knowledge.graph.KnowledgeGraphClient;
import com.superagent.business.chat.knowledge.model.KnowledgeRouteCandidate;
import com.superagent.business.chat.knowledge.service.KnowledgeManageService;
import com.superagent.business.chat.knowledge.vo.KnowledgeDocumentProfileVo;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class BusinessChatOrchestratorImpl implements BusinessChatOrchestrator {

    private static final int NORMAL_STATUS = 1;

    private static final int RECENT_EXCHANGE_WINDOW_SIZE = 6;

    private final BusinessChatMemorySummaryMapper businessChatMemorySummaryMapper;

    private final BusinessChatExchangeMapper businessChatExchangeMapper;

    private final KnowledgeGraphClient knowledgeGraphClient;

    private final KnowledgeManageService knowledgeManageService;

    @Override
    public BusinessChatExecutionPlan orchestrate(BusinessChatRuntimeContext runtimeContext) {
        BusinessChatHistoryContext historyContext = loadHistoryContext(runtimeContext);
        BusinessChatMode executionMode = runtimeContext.getTaskInfo().chatMode();
        String rewrittenQuestion = rewriteQuestion(runtimeContext.getTaskInfo().question(), historyContext);
        BusinessChatFreshnessRequirement freshnessRequirement =
                detectFreshnessRequirement(runtimeContext.getTaskInfo().question());
        List<KnowledgeRouteCandidate> knowledgeRouteCandidateList =
                routeKnowledgeCandidates(executionMode, rewrittenQuestion);
        String selectedDocumentContextText = buildSelectedDocumentContextText(runtimeContext, executionMode);
        String knowledgeRoute = routeKnowledge(executionMode, freshnessRequirement, knowledgeRouteCandidateList);
        String executionModel = runtimeContext.getTaskInfo().modelConfig().modelName();
        String intentLabel = switch (executionMode) {
            case CURRENT_DOCUMENT -> "document_question_answer";
            case KNOWLEDGE_BASE -> "knowledge_question_answer";
            case OPEN_ENDED -> "open_ended_question_answer";
        };
        String intentReason = "根据会话模式、历史上下文、知识路由和时效性要求生成本轮执行计划。";
        BusinessChatAgentType agentType = selectAgentType(executionMode);
        return new BusinessChatExecutionPlan(
                runtimeContext.getTaskInfo().question(),
                rewrittenQuestion,
                historyContext.contextText(),
                historyContext.memorySummary(),
                historyContext.recentExchangeCount(),
                selectedDocumentContextText,
                freshnessRequirement,
                knowledgeRoute,
                knowledgeRouteCandidateList,
                executionModel,
                intentLabel,
                intentReason,
                agentType,
                executionMode,
                buildExecutionStepList(
                        historyContext,
                        rewrittenQuestion,
                        selectedDocumentContextText,
                        freshnessRequirement,
                        knowledgeRoute,
                        knowledgeRouteCandidateList,
                        executionModel));
    }

    private BusinessChatAgentType selectAgentType(BusinessChatMode executionMode) {
        return switch (executionMode) {
            case KNOWLEDGE_BASE -> BusinessChatAgentType.KNOWLEDGE_QA;
            case CURRENT_DOCUMENT, OPEN_ENDED -> BusinessChatAgentType.THINK_ACT;
        };
    }

    private BusinessChatHistoryContext loadHistoryContext(BusinessChatRuntimeContext runtimeContext) {
        String memorySummary = loadConversationMemory(runtimeContext);
        List<BusinessChatRecentExchange> recentExchangeList = loadRecentExchangeList(runtimeContext);
        String contextText = buildHistoryContextText(memorySummary, recentExchangeList);
        return new BusinessChatHistoryContext(
                memorySummary,
                recentExchangeList,
                contextText);
    }

    private String loadConversationMemory(BusinessChatRuntimeContext runtimeContext) {
        BusinessChatMemorySummaryData summaryData = businessChatMemorySummaryMapper.selectOne(
                Wrappers.<BusinessChatMemorySummaryData>lambdaQuery()
                        .eq(BusinessChatMemorySummaryData::getDialogueCode, runtimeContext.getTaskInfo().conversationId())
                        .eq(BusinessChatMemorySummaryData::getStatus, NORMAL_STATUS)
                        .last("limit 1"));
        return summaryData == null ? null : requireStoredMemory(summaryData);
    }

    private List<BusinessChatRecentExchange> loadRecentExchangeList(BusinessChatRuntimeContext runtimeContext) {
        List<BusinessChatExchangeData> exchangeDataList = businessChatExchangeMapper.selectList(
                Wrappers.<BusinessChatExchangeData>lambdaQuery()
                        .eq(BusinessChatExchangeData::getDialogueCode, runtimeContext.getTaskInfo().conversationId())
                        .eq(BusinessChatExchangeData::getStatus, NORMAL_STATUS)
                        .eq(BusinessChatExchangeData::getExchangeState, BusinessChatExchangeState.COMPLETED.getDatabaseCode())
                        .orderByDesc(BusinessChatExchangeData::getCreateTime)
                        .orderByDesc(BusinessChatExchangeData::getId)
                        .last("limit " + RECENT_EXCHANGE_WINDOW_SIZE));
        List<BusinessChatExchangeData> chronologicalList = new ArrayList<>(exchangeDataList);
        Collections.reverse(chronologicalList);
        return chronologicalList.stream()
                .map(this::buildRecentExchange)
                .toList();
    }

    private BusinessChatRecentExchange buildRecentExchange(BusinessChatExchangeData exchangeData) {
        return new BusinessChatRecentExchange(
                requireStoredText(exchangeData.getUserPrompt(), "userPrompt", exchangeData.getId()),
                requireStoredText(exchangeData.getReplyContent(), "replyContent", exchangeData.getId()),
                requireStoredCreateTime(exchangeData));
    }

    private String requireStoredText(String value, String fieldName, Long exchangeId) {
        String normalizedValue = value == null ? null : value.strip();
        if (!StringUtils.hasText(normalizedValue)) {
            throw new IllegalStateException("%s is empty for exchangeId=%s".formatted(fieldName, exchangeId));
        }
        return normalizedValue;
    }

    private String requireStoredMemory(BusinessChatMemorySummaryData summaryData) {
        String summaryText = summaryData.getSummaryText() == null ? null : summaryData.getSummaryText().strip();
        if (!StringUtils.hasText(summaryText)) {
            throw new IllegalStateException(
                    "memory summary text is empty for conversation: " + summaryData.getDialogueCode());
        }
        return summaryText;
    }

    private LocalDateTime requireStoredCreateTime(BusinessChatExchangeData exchangeData) {
        if (exchangeData.getCreateTime() == null) {
            throw new IllegalStateException("createTime is empty for exchangeId=" + exchangeData.getId());
        }
        return exchangeData.getCreateTime();
    }

    private String buildHistoryContextText(
            String memorySummary,
            List<BusinessChatRecentExchange> recentExchangeList) {
        if (!StringUtils.hasText(memorySummary) && recentExchangeList.isEmpty()) {
            return null;
        }
        StringBuilder contextBuilder = new StringBuilder();
        if (StringUtils.hasText(memorySummary)) {
            contextBuilder.append("长期摘要：\n")
                    .append(memorySummary)
                    .append("\n\n");
        }
        if (!recentExchangeList.isEmpty()) {
            contextBuilder.append("最近对话：\n");
            for (BusinessChatRecentExchange recentExchange : recentExchangeList) {
                contextBuilder.append("时间：")
                        .append(recentExchange.createTime())
                        .append("\n用户：")
                        .append(recentExchange.userPrompt())
                        .append("\n助手：")
                        .append(recentExchange.replyContent())
                        .append("\n\n");
            }
        }
        return contextBuilder.toString().strip();
    }

    private String rewriteQuestion(String originalQuestion, BusinessChatHistoryContext historyContext) {
        if (!StringUtils.hasText(historyContext.contextText())) {
            return originalQuestion;
        }
        return "结合历史上下文，回答当前问题：" + originalQuestion;
    }

    private BusinessChatFreshnessRequirement detectFreshnessRequirement(String originalQuestion) {
        String detectionText = originalQuestion;
        List<String> explicitSignalList = List.of("今天", "现在", "当前", "实时", "最新", "刚刚");
        List<String> relativeSignalList = List.of("最近", "本周", "今年");
        List<String> externalObjectSignalList = List.of("价格", "股价", "汇率", "天气", "新闻", "政策", "公告", "版本", "发布");

        List<String> matchedSignalList = new ArrayList<>();
        explicitSignalList.stream()
                .filter(detectionText::contains)
                .forEach(matchedSignalList::add);
        boolean hasRelativeSignal = relativeSignalList.stream().anyMatch(detectionText::contains);
        boolean hasExternalObjectSignal = externalObjectSignalList.stream().anyMatch(detectionText::contains);
        if (hasRelativeSignal && hasExternalObjectSignal) {
            relativeSignalList.stream()
                    .filter(detectionText::contains)
                    .forEach(matchedSignalList::add);
            externalObjectSignalList.stream()
                    .filter(detectionText::contains)
                    .forEach(matchedSignalList::add);
        }

        if (matchedSignalList.isEmpty()) {
            return new BusinessChatFreshnessRequirement(
                    false,
                    "用户问题未命中明确实时信息信号",
                    List.of(),
                    "NOT_REQUIRED");
        }
        return new BusinessChatFreshnessRequirement(
                true,
                "用户问题命中实时信息信号，当前执行链路没有外部实时检索能力",
                matchedSignalList.stream().distinct().toList(),
                "UNAVAILABLE");
    }

    private List<KnowledgeRouteCandidate> routeKnowledgeCandidates(
            BusinessChatMode executionMode,
            String rewrittenQuestion) {
        if (executionMode != BusinessChatMode.KNOWLEDGE_BASE) {
            return List.of();
        }
        return knowledgeGraphClient.routeQuestion(rewrittenQuestion, 5);
    }

    private String buildSelectedDocumentContextText(
            BusinessChatRuntimeContext runtimeContext,
            BusinessChatMode executionMode) {
        if (executionMode != BusinessChatMode.CURRENT_DOCUMENT) {
            return null;
        }
        Long selectedDocumentId = runtimeContext.getTaskInfo().selectedDocumentId();
        if (selectedDocumentId == null || selectedDocumentId <= 0) {
            throw new IllegalStateException("selectedDocumentId is required for CURRENT_DOCUMENT");
        }
        KnowledgeDocumentIdRequest request = new KnowledgeDocumentIdRequest();
        request.setDocumentId(String.valueOf(selectedDocumentId));
        KnowledgeDocumentProfileVo profile = knowledgeManageService.queryDocumentProfile(request);
        String parsedText = knowledgeManageService.queryDocumentParsedText(request);
        StringBuilder builder = new StringBuilder()
                .append("文档ID：").append(selectedDocumentId).append("\n")
                .append("文档名称：").append(runtimeContext.getTaskInfo().selectedDocumentName()).append("\n");
        if (StringUtils.hasText(profile.getSummaryText())) {
            builder.append("画像摘要：").append(profile.getSummaryText()).append("\n");
        }
        appendListContext(builder, "可回答问题", profile.getAnswerableQuestions());
        appendListContext(builder, "不可回答问题", profile.getUnanswerableQuestions());
        appendListContext(builder, "业务实体", profile.getBusinessEntities());
        appendListContext(builder, "术语", profile.getTerms());
        appendListContext(builder, "问题模式", profile.getQuestionPatterns());
        builder.append("文档正文：\n").append(parsedText).append("\n");
        String contextText = builder.toString().strip();
        if (!StringUtils.hasText(contextText)) {
            throw new IllegalStateException("selected document context is empty: " + selectedDocumentId);
        }
        return contextText;
    }

    private void appendListContext(StringBuilder builder, String label, List<String> valueList) {
        if (valueList == null || valueList.isEmpty()) {
            return;
        }
        builder.append(label).append("：");
        builder.append(String.join("、", valueList.stream()
                .map(value -> value == null ? "" : value.strip())
                .filter(StringUtils::hasText)
                .toList()));
        builder.append("\n");
    }

    private String routeKnowledge(
            BusinessChatMode executionMode,
            BusinessChatFreshnessRequirement freshnessRequirement,
            List<KnowledgeRouteCandidate> knowledgeRouteCandidateList) {
        String baseRoute = switch (executionMode) {
            case CURRENT_DOCUMENT -> "CURRENT_DOCUMENT";
            case KNOWLEDGE_BASE -> knowledgeRouteCandidateList.isEmpty()
                    ? "KNOWLEDGE_BASE|NO_DOCUMENT_MATCH"
                    : "KNOWLEDGE_BASE|DOCUMENT_MATCHED";
            case OPEN_ENDED -> "NOT_REQUIRED";
        };
        if (freshnessRequirement.required()) {
            return baseRoute + "|FRESHNESS_REQUIRED";
        }
        return baseRoute;
    }

    private List<String> buildExecutionStepList(
            BusinessChatHistoryContext historyContext,
            String rewrittenQuestion,
            String selectedDocumentContextText,
            BusinessChatFreshnessRequirement freshnessRequirement,
            String knowledgeRoute,
            List<KnowledgeRouteCandidate> knowledgeRouteCandidateList,
            String executionModel) {
        return List.of(
                StringUtils.hasText(historyContext.memorySummary()) ? "加载长期摘要：已加载" : "加载长期摘要：无",
                "加载最近对话窗口：%s轮".formatted(historyContext.recentExchangeCount()),
                "问题改写：%s".formatted(rewrittenQuestion),
                "当前文档上下文：%s".formatted(StringUtils.hasText(selectedDocumentContextText) ? "已加载" : "无"),
                "时效性判断：%s".formatted(freshnessRequirement.required() ? "需要实时信息" : "不需要实时信息"),
                "知识路由：%s".formatted(knowledgeRoute),
                "路由候选文档：%s".formatted(knowledgeRouteCandidateList.size()),
                "执行模型：%s".formatted(executionModel),
                "按执行计划生成流式正文",
                "补发执行补充信息与推荐追问");
    }
}
