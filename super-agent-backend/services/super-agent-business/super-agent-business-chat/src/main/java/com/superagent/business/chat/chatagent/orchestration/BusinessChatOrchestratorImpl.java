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

    private static final int RECENT_EXCHANGE_WINDOW_SIZE = 6;

    private final BusinessChatMemorySummaryMapper businessChatMemorySummaryMapper;

    private final BusinessChatExchangeMapper businessChatExchangeMapper;

    private final KnowledgeGraphClient knowledgeGraphClient;

    private final KnowledgeManageService knowledgeManageService;

    @Override
    public BusinessChatExecutionPlan orchestrate(BusinessChatRuntimeContext runtimeContext) {
        // 编排流：运行态快照 -> 历史上下文/知识路由/时效性判断 -> 单轮执行计划。
        // Agent 不直接查数据库和图谱，只消费这里组装出的计划，避免执行层各自理解上下文。
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

    /**
     * 加载本轮可用历史上下文。
     *
     * <p>长期摘要用于压缩多轮语义，最近窗口用于保留原始问答细节。
     * 两者合并后只作为提示词上下文，不会改写数据库中的历史记录。</p>
     */
    private BusinessChatHistoryContext loadHistoryContext(BusinessChatRuntimeContext runtimeContext) {
        // 历史上下文由“长期摘要 + 最近完成轮次”组成：摘要给连续语义，最近窗口给原始问答细节。
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
        // 最近窗口先按倒序取最新 N 轮，再反转为自然时间序，保证提示词里的上下文阅读顺序稳定。
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
        // 当前不做模型改写，只显式告诉执行模型本轮问题需要结合已加载历史理解。
        // 这样不会引入第二次模型调用，也不会把原问题改写成不可追踪的新语义。
        return "结合历史上下文，回答当前问题：" + originalQuestion;
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
            List<KnowledgeRouteCandidate> knowledgeRouteCandidateList) {
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
