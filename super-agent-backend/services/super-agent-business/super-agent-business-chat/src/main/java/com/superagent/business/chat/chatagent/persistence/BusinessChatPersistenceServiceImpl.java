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
import com.superagent.idgenerator.toolkit.SnowflakeIdGenerator;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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

    private final SnowflakeIdGenerator snowflakeIdGenerator;

    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public BusinessChatTaskInfo createTurnRecordAndBuildTaskInfo(BusinessChatStartPlan startPlan) {
        // 建档流：conversation -> RUNNING dialogue -> RUNNING exchange。
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

        // 任务快照：数据库主键、会话编号、锁信息、计时起点。
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

    private void updateExchangeAndDialogue(
            BusinessChatFinalizedTurn finalizedTurn,
            BusinessChatExchangeState exchangeState,
            String finishNote) {
        // 归档流：冻结快照 -> exchange JSON 字段与终态。
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
        Map<String, Object> debugTrace = new LinkedHashMap<>();
        debugTrace.put("traceId", finalizedTurn.taskInfo().traceId());
        debugTrace.put("intentAnalysis", finalizedTurn.intentAnalysis());
        debugTrace.put("executionPlan", finalizedTurn.executionPlan());
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
