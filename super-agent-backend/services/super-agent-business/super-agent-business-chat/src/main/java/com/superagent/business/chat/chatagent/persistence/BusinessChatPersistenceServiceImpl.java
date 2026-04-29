package com.superagent.business.chat.chatagent.persistence;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superagent.business.chat.chatagent.data.BusinessChatDialogueData;
import com.superagent.business.chat.chatagent.data.BusinessChatExchangeData;
import com.superagent.business.chat.chatagent.data.BusinessChatExchangeTraceStageData;
import com.superagent.business.chat.chatagent.data.BusinessChatMemorySummaryData;
import com.superagent.business.chat.chatagent.mapper.BusinessChatDialogueMapper;
import com.superagent.business.chat.chatagent.mapper.BusinessChatExchangeMapper;
import com.superagent.business.chat.chatagent.mapper.BusinessChatExchangeTraceStageMapper;
import com.superagent.business.chat.chatagent.mapper.BusinessChatMemorySummaryMapper;
import com.superagent.business.chat.chatagent.model.BusinessChatDialogueStage;
import com.superagent.business.chat.chatagent.model.BusinessChatExchangeState;
import com.superagent.business.chat.chatagent.model.BusinessChatStartPlan;
import com.superagent.business.chat.chatagent.model.BusinessChatTaskInfo;
import com.superagent.business.chat.chatagent.runtime.BusinessChatFinalizedTurn;
import com.superagent.business.chat.chatagent.runtime.BusinessChatRuntimeContext;
import com.superagent.business.chat.chatagent.service.BusinessChatSessionStateService;
import com.superagent.idgenerator.toolkit.SnowflakeIdGenerator;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 对话归档持久化服务。
 *
 * <p>这是对话流的数据库边界。运行态中的增量事件不会直接散落写库，
 * 而是在本类中被收束成 RUNNING、COMPLETED、FAILED、STOPPED 几种明确终态。</p>
 *
 * <p>它负责四类数据：</p>
 * <ol>
 *     <li>dialogue：表示一个 conversation 的可见会话状态和标题。</li>
 *     <li>exchange：表示一轮用户问题和助手回答，是成功/失败/中止归档的主记录。</li>
 *     <li>trace stage：表示关键执行阶段，给后台观测页使用。</li>
 *     <li>memory summary：表示下一轮编排可以读取的会话摘要。</li>
 * </ol>
 *
 * <p>本类的核心原则是：运行中先创建可归档锚点，结束时只基于 FinalizedTurn 冻结快照更新终态，
 * 避免同一轮回答的正文、追踪和引用来自不同时间点。</p>
 */
@Service
@RequiredArgsConstructor
public class BusinessChatPersistenceServiceImpl implements BusinessChatPersistenceService {

    private static final int NORMAL_STATUS = 1;

    private static final int TRACE_STAGE_RUNNING = 1;

    private static final int TRACE_STAGE_COMPLETED = 2;

    private static final int TRACE_STAGE_FAILED = 3;

    private static final int TOP_LEVEL_STAGE = 1;

    private final BusinessChatDialogueMapper businessChatDialogueMapper;

    private final BusinessChatExchangeMapper businessChatExchangeMapper;

    private final BusinessChatExchangeTraceStageMapper businessChatExchangeTraceStageMapper;

    private final BusinessChatMemorySummaryMapper businessChatMemorySummaryMapper;

    private final BusinessChatSessionStateService businessChatSessionStateService;

    private final SnowflakeIdGenerator snowflakeIdGenerator;

    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public BusinessChatTaskInfo createTurnRecordAndBuildTaskInfo(BusinessChatStartPlan startPlan) {
        // 建档流：conversation -> RUNNING dialogue -> RUNNING exchange。
        // 这一步是流式执行前的持久化锚点，后续成功、失败、中止都只更新这条 exchange。
        BusinessChatDialogueData dialogueData = loadOrCreateDialogue(startPlan);
        dialogueData.setDialogueStage(BusinessChatDialogueStage.RUNNING.getDatabaseCode());
        dialogueData.setChatMode(startPlan.chatMode().getDatabaseCode());
        dialogueData.setSelectedDocumentId(startPlan.selectedDocumentId());
        dialogueData.setSelectedDocumentName(startPlan.selectedDocumentName());
        businessChatDialogueMapper.updateById(dialogueData);

        BusinessChatExchangeData exchangeData = new BusinessChatExchangeData();
        exchangeData.setId(snowflakeIdGenerator.nextId());
        exchangeData.setDialogueCode(startPlan.conversationId());
        exchangeData.setUserPrompt(startPlan.question());
        exchangeData.setReplyContent("");
        exchangeData.setReasoningNoteList("[]");
        exchangeData.setSourceSnapshotList("[]");
        exchangeData.setFollowupSuggestionList("[]");
        exchangeData.setToolTraceList("[]");
        exchangeData.setExchangeState(BusinessChatExchangeState.RUNNING.getDatabaseCode());
        exchangeData.setStatus(NORMAL_STATUS);
        businessChatExchangeMapper.insert(exchangeData);
        businessChatSessionStateService.activate(startPlan.conversationId());

        // 任务快照：数据库主键、会话编号、锁信息、计时起点。
        // RuntimeContext 不再反查这些入口信息，避免执行中读到被其他请求更新后的 dialogue 状态。
        return new BusinessChatTaskInfo(
                dialogueData.getId(),
                exchangeData.getId(),
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
    }

    @Override
    @Transactional
    public void archiveSucceededTurn(BusinessChatFinalizedTurn finalizedTurn) {
        updateExchangeAndDialogue(finalizedTurn, BusinessChatExchangeState.COMPLETED, null);
    }

    @Override
    public boolean dialogueTitleExists(BusinessChatFinalizedTurn finalizedTurn) {
        BusinessChatDialogueData dialogueData = businessChatDialogueMapper.selectById(finalizedTurn.taskInfo().dialogueId());
        if (dialogueData == null) {
            throw new IllegalStateException("dialogue does not exist: " + finalizedTurn.taskInfo().dialogueId());
        }
        return StringUtils.hasText(dialogueData.getDialogueTitle());
    }

    @Override
    @Transactional
    public void updateDialogueTitleIfAbsent(BusinessChatFinalizedTurn finalizedTurn, String dialogueTitle) {
        int affectedRows = businessChatDialogueMapper.update(
                null,
                Wrappers.<BusinessChatDialogueData>lambdaUpdate()
                        .set(BusinessChatDialogueData::getDialogueTitle, dialogueTitle)
                        .eq(BusinessChatDialogueData::getId, finalizedTurn.taskInfo().dialogueId())
                        .eq(BusinessChatDialogueData::getStatus, NORMAL_STATUS)
                        .and(wrapper -> wrapper
                                .isNull(BusinessChatDialogueData::getDialogueTitle)
                                .or()
                                .eq(BusinessChatDialogueData::getDialogueTitle, "")));
        if (affectedRows > 1) {
            throw new IllegalStateException("dialogue title update affected multiple rows.");
        }
    }

    @Override
    @Transactional
    public void archiveFailedTurn(BusinessChatRuntimeContext runtimeContext, String finishNote) {
        updateExchangeAndDialogue(runtimeContext.freezeFinalizedTurn(), BusinessChatExchangeState.FAILED, finishNote);
    }

    @Override
    @Transactional
    public void archiveStoppedTurn(BusinessChatRuntimeContext runtimeContext, String finishNote) {
        updateExchangeAndDialogue(runtimeContext.freezeFinalizedTurn(), BusinessChatExchangeState.STOPPED, finishNote);
    }

    @Override
    @Transactional
    public void refreshConversationSummary(BusinessChatFinalizedTurn finalizedTurn) {
        // 摘要流：成功归档快照 -> conversation 摘要。
        // 当前摘要只记录最新一轮问答和覆盖游标，编排器再叠加最近窗口形成上下文。
        BusinessChatMemorySummaryData summaryData = businessChatMemorySummaryMapper.selectOne(
                Wrappers.<BusinessChatMemorySummaryData>lambdaQuery()
                        .eq(BusinessChatMemorySummaryData::getDialogueCode, finalizedTurn.taskInfo().conversationId())
                        .eq(BusinessChatMemorySummaryData::getStatus, NORMAL_STATUS)
                        .last("limit 1"));
        long exchangeCount = businessChatExchangeMapper.selectCount(
                Wrappers.<BusinessChatExchangeData>lambdaQuery()
                        .eq(BusinessChatExchangeData::getDialogueCode, finalizedTurn.taskInfo().conversationId())
                        .eq(BusinessChatExchangeData::getStatus, NORMAL_STATUS));

        if (summaryData == null) {
            summaryData = new BusinessChatMemorySummaryData();
            summaryData.setId(snowflakeIdGenerator.nextId());
            summaryData.setDialogueCode(finalizedTurn.taskInfo().conversationId());
            summaryData.setCompressionCount(0);
            summaryData.setSummaryVersion(1);
            summaryData.setStatus(NORMAL_STATUS);
            fillSummary(finalizedTurn, summaryData, Math.toIntExact(exchangeCount));
            businessChatMemorySummaryMapper.insert(summaryData);
            return;
        }

        summaryData.setSummaryVersion(summaryData.getSummaryVersion() + 1);
        fillSummary(finalizedTurn, summaryData, Math.toIntExact(exchangeCount));
        businessChatMemorySummaryMapper.updateById(summaryData);
    }

    /**
     * 创建本轮 trace stage。
     *
     * <p>trace stage 是后台观测维度，不参与回答生成。
     * 它记录阶段名称、顺序、耗时、快照和错误，用于解释一轮回答如何完成。</p>
     */
    @Override
    @Transactional
    public Long startTraceStage(
            BusinessChatRuntimeContext runtimeContext,
            String stageCode,
            String stageName,
            int stageOrder) {
        BusinessChatExchangeTraceStageData traceStageData = new BusinessChatExchangeTraceStageData();
        traceStageData.setId(snowflakeIdGenerator.nextId());
        traceStageData.setDialogueCode(runtimeContext.getTaskInfo().conversationId());
        traceStageData.setExchangeId(runtimeContext.getTaskInfo().exchangeId());
        traceStageData.setTraceId(runtimeContext.getTaskInfo().traceId());
        traceStageData.setStageCode(stageCode);
        traceStageData.setStageName(stageName);
        traceStageData.setStageOrder(stageOrder);
        traceStageData.setStageLevel(TOP_LEVEL_STAGE);
        traceStageData.setExecutionMode(runtimeContext.getTaskInfo().chatMode().getValue());
        traceStageData.setStageState(TRACE_STAGE_RUNNING);
        traceStageData.setStartTime(LocalDateTime.now());
        traceStageData.setStatus(NORMAL_STATUS);
        businessChatExchangeTraceStageMapper.insert(traceStageData);
        return traceStageData.getId();
    }

    /**
     * 完成 trace stage 并写入阶段快照。
     *
     * <p>snapshot 可以是执行计划、推荐追问、收尾统计等对象，会统一序列化成 JSON。</p>
     */
    @Override
    @Transactional
    public void completeTraceStage(Long traceStageId, String summaryText, Object snapshot) {
        if (traceStageId == null) {
            return;
        }
        BusinessChatExchangeTraceStageData existingTraceStageData =
                businessChatExchangeTraceStageMapper.selectById(traceStageId);
        if (existingTraceStageData == null) {
            throw new IllegalStateException("trace stage does not exist: " + traceStageId);
        }
        LocalDateTime endTime = LocalDateTime.now();
        BusinessChatExchangeTraceStageData traceStageData = new BusinessChatExchangeTraceStageData();
        traceStageData.setId(traceStageId);
        traceStageData.setStageState(TRACE_STAGE_COMPLETED);
        traceStageData.setEndTime(endTime);
        traceStageData.setDurationMs(java.time.Duration.between(existingTraceStageData.getStartTime(), endTime).toMillis());
        traceStageData.setSummaryText(summaryText);
        traceStageData.setSnapshotJson(writeJson(snapshot));
        businessChatExchangeTraceStageMapper.updateById(traceStageData);
    }

    @Override
    @Transactional
    public void failTraceStage(Long traceStageId, Throwable error) {
        if (traceStageId == null) {
            return;
        }
        BusinessChatExchangeTraceStageData existingTraceStageData =
                businessChatExchangeTraceStageMapper.selectById(traceStageId);
        if (existingTraceStageData == null) {
            throw new IllegalStateException("trace stage does not exist: " + traceStageId);
        }
        LocalDateTime endTime = LocalDateTime.now();
        BusinessChatExchangeTraceStageData traceStageData = new BusinessChatExchangeTraceStageData();
        traceStageData.setId(traceStageId);
        traceStageData.setStageState(TRACE_STAGE_FAILED);
        traceStageData.setEndTime(endTime);
        traceStageData.setDurationMs(java.time.Duration.between(existingTraceStageData.getStartTime(), endTime).toMillis());
        traceStageData.setErrorMessage(error.getMessage());
        businessChatExchangeTraceStageMapper.updateById(traceStageData);
    }

    private BusinessChatDialogueData loadOrCreateDialogue(BusinessChatStartPlan startPlan) {
        BusinessChatDialogueData dialogueData = businessChatDialogueMapper.selectOne(
                Wrappers.<BusinessChatDialogueData>lambdaQuery()
                        .eq(BusinessChatDialogueData::getDialogueCode, startPlan.conversationId())
                        .eq(BusinessChatDialogueData::getStatus, NORMAL_STATUS)
                        .last("limit 1"));
        if (dialogueData != null) {
            return dialogueData;
        }

        dialogueData = new BusinessChatDialogueData();
        dialogueData.setId(snowflakeIdGenerator.nextId());
        dialogueData.setDialogueCode(startPlan.conversationId());
        dialogueData.setDialogueTitle("");
        dialogueData.setDialogueStage(BusinessChatDialogueStage.IDLE.getDatabaseCode());
        dialogueData.setChatMode(startPlan.chatMode().getDatabaseCode());
        dialogueData.setStatus(NORMAL_STATUS);
        businessChatDialogueMapper.insert(dialogueData);
        return dialogueData;
    }

    /**
     * 归档一轮 exchange 并恢复 dialogue 空闲态。
     *
     * <p>成功、失败、中止三条路径都会进入这里，差异只体现在 exchangeState 和 finishNote。
     * 这样终态字段、debugTrace、延迟指标和会话状态更新保持同一套写入逻辑。</p>
     */
    private void updateExchangeAndDialogue(
            BusinessChatFinalizedTurn finalizedTurn,
            BusinessChatExchangeState exchangeState,
            String finishNote) {
        // 归档流：冻结快照 -> exchange JSON 字段与终态。
        // 所有可复盘信息都来自同一个 FinalizedTurn，避免 reply、trace、sources 属于不同时间点。
        BusinessChatExchangeData exchangeData = new BusinessChatExchangeData();
        exchangeData.setId(finalizedTurn.taskInfo().exchangeId());
        exchangeData.setReplyContent(finalizedTurn.replyContent());
        exchangeData.setReasoningNoteList(writeJson(finalizedTurn.reasoningNoteList()));
        exchangeData.setSourceSnapshotList(writeJson(finalizedTurn.sourceSnapshotList()));
        exchangeData.setFollowupSuggestionList(writeJson(finalizedTurn.followUpSuggestionList()));
        exchangeData.setToolTraceList(writeJson(finalizedTurn.toolTraceList()));
        exchangeData.setDebugTraceJson(writeJson(buildDebugTrace(finalizedTurn)));
        exchangeData.setExchangeState(exchangeState.getDatabaseCode());
        exchangeData.setFinishNote(finishNote);
        exchangeData.setFirstTokenLatencyMs(normalizeLatency(finalizedTurn.firstTokenLatencyMs()));
        exchangeData.setTotalLatencyMs(finalizedTurn.totalLatencyMs());
        businessChatExchangeMapper.updateById(exchangeData);

        // 会话状态：本轮终态落库后 dialogue 回到 IDLE。
        // conversationId 的并发保护由 Redis lease 负责，dialogueStage 只表达后台和前台可见状态。
        BusinessChatDialogueData dialogueData = new BusinessChatDialogueData();
        dialogueData.setId(finalizedTurn.taskInfo().dialogueId());
        dialogueData.setDialogueStage(BusinessChatDialogueStage.IDLE.getDatabaseCode());
        dialogueData.setChatMode(finalizedTurn.taskInfo().chatMode().getDatabaseCode());
        businessChatDialogueMapper.updateById(dialogueData);
    }

    private void fillSummary(
            BusinessChatFinalizedTurn finalizedTurn,
            BusinessChatMemorySummaryData summaryData,
            int exchangeCount) {
        // 摘要覆盖：记录本轮 exchange 游标与最近问答文本。
        // coveredExchangeId/Count 用于说明这份摘要覆盖到哪一轮，summaryText 用于直接进入下一轮提示词。
        summaryData.setCoveredExchangeId(finalizedTurn.taskInfo().exchangeId());
        summaryData.setCoveredExchangeCount(exchangeCount);
        summaryData.setSummaryText("""
                会话编号：%s
                最近问题：%s
                最近回答：%s
                """.formatted(
                finalizedTurn.taskInfo().conversationId(),
                finalizedTurn.taskInfo().question(),
                finalizedTurn.replyContent()));
        summaryData.setSummaryJson(writeJson(Map.of(
                "conversationId", finalizedTurn.taskInfo().conversationId(),
                "chatMode", finalizedTurn.taskInfo().chatMode().getValue(),
                "coveredExchangeId", finalizedTurn.taskInfo().exchangeId(),
                "coveredExchangeCount", exchangeCount,
                "latestQuestion", finalizedTurn.taskInfo().question(),
                "latestReply", finalizedTurn.replyContent())));
        summaryData.setLastSourceEditTime(LocalDateTime.now());
    }

    private Map<String, Object> buildDebugTrace(BusinessChatFinalizedTurn finalizedTurn) {
        // debugTrace 是后台追踪页的数据源：保存当时的意图分析、执行计划和租约标识，
        // 后续页面查询只解析这份快照，不重新运行编排逻辑。
        Map<String, Object> debugTrace = new LinkedHashMap<>();
        debugTrace.put("traceId", finalizedTurn.taskInfo().traceId());
        debugTrace.put("intentAnalysis", finalizedTurn.intentAnalysis());
        debugTrace.put("executionPlan", finalizedTurn.executionPlan());
        debugTrace.put("modelCallCount", finalizedTurn.modelCallCount());
        debugTrace.put("leaseKey", finalizedTurn.taskInfo().leaseKey());
        return debugTrace;
    }

    private Long normalizeLatency(long latencyMs) {
        return latencyMs < 0 ? null : latencyMs;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize chat archive payload.", exception);
        }
    }
}
