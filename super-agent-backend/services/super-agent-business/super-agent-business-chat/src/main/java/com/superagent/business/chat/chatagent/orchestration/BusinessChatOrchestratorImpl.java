package com.superagent.business.chat.chatagent.orchestration;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.superagent.business.chat.chatagent.agent.BusinessChatAgentType;
import com.superagent.business.chat.chatagent.config.BusinessChatClarificationProperties;
import com.superagent.business.chat.chatagent.config.BusinessChatHistorySummaryProperties;
import com.superagent.business.chat.knowledge.config.KnowledgeRouteProperties;
import com.superagent.business.chat.chatagent.data.BusinessChatExchangeData;
import com.superagent.business.chat.chatagent.data.BusinessChatMemorySummaryData;
import com.superagent.business.chat.chatagent.mapper.BusinessChatExchangeMapper;
import com.superagent.business.chat.chatagent.mapper.BusinessChatMemorySummaryMapper;
import com.superagent.business.chat.chatagent.model.BusinessChatClarificationOption;
import com.superagent.business.chat.chatagent.model.BusinessChatClarificationPlan;
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
import com.superagent.business.chat.knowledge.service.KnowledgeRouteTraceService;
import com.superagent.business.chat.knowledge.vo.KnowledgeDocumentProfileVo;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 对话执行计划编排器。
 *
 * <p>这是模型调用前的“计划生成层”。它不直接调用模型输出正文，而是把本轮回答需要的上下文
 * 统一整理成 {@link BusinessChatExecutionPlan}，再交给 Agent 执行。</p>
 *
 * <p>输入来自 {@link BusinessChatRuntimeContext}：用户问题、会话模式、模型配置、当前文档选择。
 * 编排时再补充长期摘要、最近完成轮次、当前文档正文、知识路由候选和时效性判断。</p>
 *
 * <p>输出的执行计划会同时进入两个下游：</p>
 * <ol>
 *     <li>Agent 使用它组织提示词和回答边界。</li>
 *     <li>归档层把它写入 debugTraceJson，供后台追踪页复盘当时的执行计划。</li>
 * </ol>
 */
@Service
@RequiredArgsConstructor
public class BusinessChatOrchestratorImpl implements BusinessChatOrchestrator {

    private static final int NORMAL_STATUS = 1;

    private final BusinessChatMemorySummaryMapper businessChatMemorySummaryMapper;

    private final BusinessChatExchangeMapper businessChatExchangeMapper;

    private final KnowledgeGraphClient knowledgeGraphClient;

    private final KnowledgeManageService knowledgeManageService;

    private final KnowledgeRouteTraceService knowledgeRouteTraceService;

    private final BusinessChatHistorySummaryProperties historySummaryProperties;

    private final BusinessChatClarificationProperties clarificationProperties;

    private final KnowledgeRouteProperties routeProperties;

    private final BusinessChatQuestionRewriteService questionRewriteService;

    @Override
    public BusinessChatExecutionPlan orchestrate(BusinessChatRuntimeContext runtimeContext) {
        // 编排流：运行态快照 -> 历史上下文/知识路由/时效性判断 -> 单轮执行计划。
        // Agent 不直接查数据库和图谱，只消费这里组装出的计划，避免执行层各自理解上下文。
        BusinessChatHistoryContext historyContext = loadHistoryContext(runtimeContext);
        BusinessChatMode executionMode = runtimeContext.getTaskInfo().chatMode();
        String rewrittenQuestion = questionRewriteService.rewrite(
                runtimeContext,
                runtimeContext.getTaskInfo().question(),
                historyContext.rewriteContextText(),
                runtimeContext.getTaskInfo().modelConfig());
        BusinessChatFreshnessRequirement freshnessRequirement =
                detectFreshnessRequirement(runtimeContext.getTaskInfo().question());
        List<KnowledgeRouteCandidate> knowledgeRouteCandidateList =
                routeKnowledgeCandidates(executionMode, rewrittenQuestion);
        recordKnowledgeRouteTrace(runtimeContext, executionMode, rewrittenQuestion, knowledgeRouteCandidateList);
        BusinessChatClarificationPlan clarificationPlan =
                buildClarificationPlan(executionMode, knowledgeRouteCandidateList);
        String selectedDocumentContextText = buildSelectedDocumentContextText(runtimeContext, executionMode);
        String knowledgeRoute = routeKnowledge(
                executionMode,
                freshnessRequirement,
                knowledgeRouteCandidateList,
                clarificationPlan);
        String executionModel = runtimeContext.getTaskInfo().modelConfig().modelName();
        String intentLabel = buildIntentLabel(executionMode, clarificationPlan);
        String intentReason = clarificationPlan.required()
                ? clarificationPlan.reason()
                : "根据会话模式、历史上下文、知识路由和时效性要求生成本轮执行计划。";
        BusinessChatAgentType agentType = selectAgentType(executionMode, clarificationPlan);
        return new BusinessChatExecutionPlan(
                runtimeContext.getTaskInfo().question(),
                rewrittenQuestion,
                historyContext.rewriteContextText(),
                historyContext.answerContextText(),
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
                clarificationPlan,
                buildExecutionStepList(
                        historyContext,
                        rewrittenQuestion,
                        selectedDocumentContextText,
                        freshnessRequirement,
                        knowledgeRoute,
                        knowledgeRouteCandidateList,
                        clarificationPlan,
                        executionModel));
    }

    private String buildIntentLabel(BusinessChatMode executionMode, BusinessChatClarificationPlan clarificationPlan) {
        if (clarificationPlan.required()) {
            return "knowledge_route_clarification";
        }
        return switch (executionMode) {
            case CURRENT_DOCUMENT -> "document_question_answer";
            case KNOWLEDGE_BASE -> "knowledge_question_answer";
            case OPEN_ENDED -> "open_ended_question_answer";
        };
    }

    private BusinessChatAgentType selectAgentType(
            BusinessChatMode executionMode,
            BusinessChatClarificationPlan clarificationPlan) {
        if (clarificationPlan.required()) {
            return BusinessChatAgentType.CLARIFICATION;
        }
        return switch (executionMode) {
            case KNOWLEDGE_BASE -> BusinessChatAgentType.KNOWLEDGE_QA;
            case CURRENT_DOCUMENT, OPEN_ENDED -> BusinessChatAgentType.THINK_ACT;
        };
    }

    /**
     * 加载本轮可用历史上下文。
     *
     * <p>长期摘要用于压缩多轮语义，最近窗口用于保留原始问答细节。
     * 两者合并后只作为提示词上下文，不会改写数据库中的历史记录。</p>
     */
    private BusinessChatHistoryContext loadHistoryContext(BusinessChatRuntimeContext runtimeContext) {
        if (!Boolean.TRUE.equals(historySummaryProperties.getEnabled())) {
            return new BusinessChatHistoryContext(null, null, null, List.of());
        }
        // 历史上下文由“长期摘要 + 最近完成轮次”组成：摘要给连续语义，最近窗口给原始问答细节。
        String memorySummary = loadConversationMemory(runtimeContext);
        List<BusinessChatRecentExchange> recentExchangeList = loadRecentExchangeList(runtimeContext);
        String rewriteContextText = buildRewriteHistoryContextText(memorySummary, recentExchangeList);
        String answerContextText = buildAnswerHistoryContextText(memorySummary, recentExchangeList);
        return new BusinessChatHistoryContext(
                rewriteContextText,
                answerContextText,
                memorySummary,
                recentExchangeList);
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
        // 最近窗口先按倒序取最新 N 轮，再反转为自然时间序，保证提示词里的上下文阅读顺序稳定。
        List<BusinessChatExchangeData> exchangeDataList = businessChatExchangeMapper.selectList(
                Wrappers.<BusinessChatExchangeData>lambdaQuery()
                        .eq(BusinessChatExchangeData::getDialogueCode, runtimeContext.getTaskInfo().conversationId())
                        .eq(BusinessChatExchangeData::getStatus, NORMAL_STATUS)
                        .eq(BusinessChatExchangeData::getExchangeState, BusinessChatExchangeState.COMPLETED.getDatabaseCode())
                        .orderByDesc(BusinessChatExchangeData::getCreateTime)
                        .orderByDesc(BusinessChatExchangeData::getId)
                        .last("limit " + historySummaryProperties.getKeepRecentTurns()));
        List<BusinessChatExchangeData> chronologicalList = new ArrayList<>(exchangeDataList);
        Collections.reverse(chronologicalList);
        return chronologicalList.stream()
                .limit(historySummaryProperties.getKeepRecentTurns())
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
        return limitText(summaryText, historySummaryProperties.getSummaryMaxChars());
    }

    private LocalDateTime requireStoredCreateTime(BusinessChatExchangeData exchangeData) {
        if (exchangeData.getCreateTime() == null) {
            throw new IllegalStateException("createTime is empty for exchangeId=" + exchangeData.getId());
        }
        return exchangeData.getCreateTime();
    }

    private String buildRewriteHistoryContextText(
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
                        .append("\n\n");
            }
        }
        return limitText(contextBuilder.toString().strip(), historySummaryProperties.getRecentTranscriptMaxChars());
    }

    private String buildAnswerHistoryContextText(
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
        return limitText(contextBuilder.toString().strip(), historySummaryProperties.getRecentTranscriptMaxChars());
    }

    private String limitText(String value, int maxChars) {
        if (!StringUtils.hasText(value) || value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars).strip();
    }

    /**
     * 判断问题是否需要实时信息。
     *
     * <p>当前系统没有外部实时检索执行器，所以这里的作用是把“需要实时但不可用”的事实写进执行计划，
     * 让 Agent 在回答时明确边界，而不是假装已经检索过。</p>
     */
    private BusinessChatFreshnessRequirement detectFreshnessRequirement(String originalQuestion) {
        // 时效性只识别明确实时词，或“相对时间 + 外部对象”的组合，避免把普通“最近聊过”误判为实时检索需求。
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
        // 知识库模式在编排阶段只产出路由候选，不在这里读取正文。
        // 正文证据由执行器按计划组织提示词，保持“召回”和“回答”两个职责分离。
        return knowledgeGraphClient.routeQuestion(rewrittenQuestion, 5);
    }

    private void recordKnowledgeRouteTrace(
            BusinessChatRuntimeContext runtimeContext,
            BusinessChatMode executionMode,
            String rewrittenQuestion,
            List<KnowledgeRouteCandidate> routeCandidateList) {
        if (executionMode != BusinessChatMode.KNOWLEDGE_BASE && executionMode != BusinessChatMode.CURRENT_DOCUMENT) {
            return;
        }
        // 当前文档模式只跑影子路由做质量观测，不改变用户手动选择文档的回答路径。
        List<KnowledgeRouteCandidate> traceCandidateList = executionMode == BusinessChatMode.CURRENT_DOCUMENT
                ? knowledgeGraphClient.routeQuestion(rewrittenQuestion, 5)
                : routeCandidateList;
        knowledgeRouteTraceService.recordRouteTrace(
                runtimeContext.getTaskInfo().traceId(),
                runtimeContext.getTaskInfo().conversationId(),
                runtimeContext.getTaskInfo().exchangeId(),
                runtimeContext.getTaskInfo().question(),
                rewrittenQuestion,
                buildIntentLabel(executionMode, BusinessChatClarificationPlan.notRequired()),
                executionMode == BusinessChatMode.CURRENT_DOCUMENT ? "SHADOW" : "AUTO",
                executionMode == BusinessChatMode.CURRENT_DOCUMENT
                        ? runtimeContext.getTaskInfo().selectedDocumentId()
                        : null,
                traceCandidateList);
    }

    private BusinessChatClarificationPlan buildClarificationPlan(
            BusinessChatMode executionMode,
            List<KnowledgeRouteCandidate> candidateList) {
        if (executionMode != BusinessChatMode.KNOWLEDGE_BASE || !clarificationProperties.isEnabled()) {
            return BusinessChatClarificationPlan.notRequired();
        }
        List<BusinessChatClarificationOption> optionList = buildClarificationOptionList(candidateList);
        if (candidateList == null || candidateList.isEmpty()) {
            return new BusinessChatClarificationPlan(
                    true,
                    "知识路由没有召回候选文档",
                    "当前没有匹配到稳定的候选文档。请补充文档名、业务范围或更具体的关键词后再试。",
                    optionList);
        }
        KnowledgeRouteCandidate topCandidate = candidateList.get(0);
        double confidence = calculateRouteConfidence(candidateList);
        if (confidence < routeProperties.getSuccessConfidence()) {
            return new BusinessChatClarificationPlan(
                    true,
                    "知识路由置信度低于阈值：%s".formatted(confidence),
                    buildClarificationReply(optionList),
                    optionList);
        }
        if (topCandidate.score() < clarificationProperties.getMinTopScore()) {
            return new BusinessChatClarificationPlan(
                    true,
                    "知识路由最高候选分数低于阈值：%s".formatted(topCandidate.score()),
                    buildClarificationReply(optionList),
                    optionList);
        }
        if (candidateList.size() < 2) {
            return BusinessChatClarificationPlan.notRequired();
        }
        KnowledgeRouteCandidate secondCandidate = candidateList.get(1);
        double scoreGap = topCandidate.score() - secondCandidate.score();
        boolean crossScope = !java.util.Objects.equals(topCandidate.scopeCode(), secondCandidate.scopeCode());
        if (crossScope && scoreGap <= clarificationProperties.getAmbiguousScoreGap()) {
            return new BusinessChatClarificationPlan(
                    true,
                    "知识路由 Top1/Top2 跨知识域且分差过小：%s".formatted(scoreGap),
                    buildClarificationReply(optionList),
                    optionList);
        }
        return BusinessChatClarificationPlan.notRequired();
    }

    private double calculateRouteConfidence(List<KnowledgeRouteCandidate> candidateList) {
        if (candidateList == null || candidateList.isEmpty()) {
            return 0D;
        }
        double topScore = candidateList.get(0).score();
        double secondScore = candidateList.size() > 1 ? candidateList.get(1).score() : 0D;
        return topScore / Math.max(10D, topScore + secondScore + 5D);
    }

    private List<BusinessChatClarificationOption> buildClarificationOptionList(
            List<KnowledgeRouteCandidate> candidateList) {
        if (candidateList == null || candidateList.isEmpty()) {
            return List.of();
        }
        return candidateList.stream()
                .limit(clarificationProperties.getMaxOptions())
                .map(this::toClarificationOption)
                .toList();
    }

    private BusinessChatClarificationOption toClarificationOption(KnowledgeRouteCandidate candidate) {
        String documentName = candidate.documentName() == null ? null : candidate.documentName().strip();
        if (!StringUtils.hasText(documentName)) {
            throw new IllegalStateException("knowledge route candidate documentName is empty: " + candidate.documentId());
        }
        return new BusinessChatClarificationOption(
                candidate.documentId(),
                documentName,
                candidate.scopeCode(),
                candidate.scopeName(),
                candidate.topicCode(),
                candidate.topicName(),
                candidate.score());
    }

    private String buildClarificationReply(List<BusinessChatClarificationOption> optionList) {
        if (optionList.isEmpty()) {
            return "当前没有匹配到稳定的候选文档。请补充文档名、业务范围或更具体的关键词后再试。";
        }
        StringBuilder builder = new StringBuilder("这个问题目前存在文档范围歧义，我先确认你想问哪一份：\n");
        for (int index = 0; index < optionList.size(); index++) {
            builder.append(index + 1)
                    .append(". 《")
                    .append(optionList.get(index).documentName())
                    .append("》\n");
        }
        builder.append("\n你可以直接回复文档名，或者切换到当前文档问答模式明确指定文档。");
        return builder.toString();
    }

    /**
     * 构建当前文档模式的上下文文本。
     *
     * <p>当前文档问答必须同时加载画像和解析正文：画像约束可回答范围，正文提供事实依据。
     * 如果任一环节缺失，会在知识管理服务中直接失败。</p>
     */
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
        // 当前文档模式必须把画像和解析正文一起装入上下文：
        // 画像告诉模型文档能回答什么，正文提供可引用事实，二者共同限定回答边界。
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

    /**
     * 生成路由摘要字符串。
     *
     * <p>这个字符串主要给 debugTrace 和前端追踪页阅读，用来快速判断本轮是否走知识库、
     * 是否匹配到候选文档、是否存在实时信息缺口。</p>
     */
    private String routeKnowledge(
            BusinessChatMode executionMode,
            BusinessChatFreshnessRequirement freshnessRequirement,
            List<KnowledgeRouteCandidate> knowledgeRouteCandidateList,
            BusinessChatClarificationPlan clarificationPlan) {
        String baseRoute = switch (executionMode) {
            case CURRENT_DOCUMENT -> "CURRENT_DOCUMENT";
            case KNOWLEDGE_BASE -> knowledgeRouteCandidateList.isEmpty()
                    ? "KNOWLEDGE_BASE|NO_DOCUMENT_MATCH"
                    : "KNOWLEDGE_BASE|DOCUMENT_MATCHED";
            case OPEN_ENDED -> "NOT_REQUIRED";
        };
        // 路由字符串进入 debugTrace 和前端追踪页，是本轮是否走知识资产、是否缺实时能力的业务摘要。
        // 它不是执行分支开关，真正的分支已经由 executionMode 和候选列表确定。
        if (freshnessRequirement.required()) {
            baseRoute = baseRoute + "|FRESHNESS_REQUIRED";
        }
        if (clarificationPlan.required()) {
            baseRoute = baseRoute + "|CLARIFICATION_REQUIRED";
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
            BusinessChatClarificationPlan clarificationPlan,
            String executionModel) {
        return List.of(
                StringUtils.hasText(historyContext.memorySummary()) ? "加载长期摘要：已加载" : "加载长期摘要：无",
                "加载最近对话窗口：%s轮".formatted(historyContext.recentExchangeCount()),
                "问题改写历史上下文：%s".formatted(StringUtils.hasText(historyContext.rewriteContextText()) ? "有" : "无"),
                "问题改写：%s".formatted(rewrittenQuestion),
                "当前文档上下文：%s".formatted(StringUtils.hasText(selectedDocumentContextText) ? "已加载" : "无"),
                "时效性判断：%s".formatted(freshnessRequirement.required() ? "需要实时信息" : "不需要实时信息"),
                "知识路由：%s".formatted(knowledgeRoute),
                "路由候选文档：%s".formatted(knowledgeRouteCandidateList.size()),
                "歧义澄清：%s".formatted(clarificationPlan.required() ? clarificationPlan.reason() : "不需要"),
                "执行模型：%s".formatted(executionModel),
                "按执行计划生成流式正文",
                "补发执行补充信息与推荐追问");
    }
}
