package com.superagent.business.chat.chatagent.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superagent.business.chat.chatagent.persistence.data.BusinessChatDialogueData;
import com.superagent.business.chat.chatagent.persistence.data.BusinessChatExchangeData;
import com.superagent.business.chat.chatagent.persistence.data.BusinessChatExchangeTraceStageData;
import com.superagent.business.chat.chatagent.persistence.data.BusinessChatModelCallTraceData;
import com.superagent.business.chat.chatagent.persistence.data.BusinessChatMemorySummaryData;
import com.superagent.business.chat.chatagent.persistence.data.BusinessChatToolCallTraceData;
import com.superagent.business.chat.chatagent.api.dto.BusinessChatExchangeDetailRequest;
import com.superagent.business.chat.chatagent.api.dto.BusinessChatSessionDetailRequest;
import com.superagent.business.chat.chatagent.api.dto.BusinessChatSessionListRequest;
import com.superagent.business.chat.chatagent.persistence.mapper.BusinessChatDialogueMapper;
import com.superagent.business.chat.chatagent.persistence.mapper.BusinessChatExchangeMapper;
import com.superagent.business.chat.chatagent.persistence.mapper.BusinessChatExchangeTraceStageMapper;
import com.superagent.business.chat.chatagent.persistence.mapper.BusinessChatModelCallTraceMapper;
import com.superagent.business.chat.chatagent.persistence.mapper.BusinessChatMemorySummaryMapper;
import com.superagent.business.chat.chatagent.persistence.mapper.BusinessChatToolCallTraceMapper;
import com.superagent.business.chat.chatagent.config.BusinessChatRuntimeProperties;
import com.superagent.business.chat.chatagent.persistence.model.BusinessChatDialogueStage;
import com.superagent.business.chat.chatagent.persistence.model.BusinessChatExchangeState;
import com.superagent.business.chat.chatagent.orchestration.model.BusinessChatMode;
import com.superagent.business.chat.chatagent.persistence.model.BusinessChatSessionListRow;
import com.superagent.business.chat.chatagent.service.BusinessChatErrorCode;
import com.superagent.business.chat.chatagent.service.BusinessChatQueryService;
import com.superagent.business.chat.chatagent.service.BusinessChatSessionStateService;
import com.superagent.business.chat.chatagent.api.vo.BusinessChatSessionDetailVo;
import com.superagent.business.chat.chatagent.api.vo.BusinessChatSessionExchangeVo;
import com.superagent.business.chat.chatagent.api.vo.BusinessChatSessionListItemVo;
import com.superagent.business.chat.chatagent.api.vo.BusinessChatSessionListPageVo;
import com.superagent.business.chat.chatagent.api.vo.BusinessChatExchangeDetailVo;
import com.superagent.business.chat.chatagent.api.vo.BusinessChatExchangeTraceStageVo;
import com.superagent.business.chat.chatagent.api.vo.BusinessChatExchangeUsageSummaryVo;
import com.superagent.business.chat.chatagent.api.vo.BusinessChatModelCallTraceVo;
import com.superagent.business.chat.chatagent.api.vo.BusinessChatToolCallTraceVo;
import com.superagent.business.chat.auth.AuthRole;
import com.superagent.business.chat.auth.AuthSessionContext;
import com.superagent.business.chat.auth.AuthSessionHolder;
import com.superagent.business.chat.support.BusinessInputValidator;
import com.superagent.common.frame.exception.BaseException;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 会话查询服务。
 *
 * <p>面向管理端读取已归档会话，把 dialogue、exchange、摘要和当前会话游标组装成前端展示模型。</p>
 */
@Service
@RequiredArgsConstructor
public class BusinessChatQueryServiceImpl implements BusinessChatQueryService {

    private static final int NORMAL_STATUS = 1;

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private final BusinessChatDialogueMapper businessChatDialogueMapper;

    private final BusinessChatExchangeMapper businessChatExchangeMapper;

    private final BusinessChatMemorySummaryMapper businessChatMemorySummaryMapper;

    private final BusinessChatExchangeTraceStageMapper businessChatExchangeTraceStageMapper;

    private final BusinessChatModelCallTraceMapper businessChatModelCallTraceMapper;

    private final BusinessChatToolCallTraceMapper businessChatToolCallTraceMapper;

    private final BusinessChatSessionStateService businessChatSessionStateService;

    private final BusinessChatRuntimeProperties runtimeProperties;

    private final ObjectMapper objectMapper;

    @Override
    public BusinessChatSessionListPageVo listSessionsPage(BusinessChatSessionListRequest request) {
        // 列表查询把前端筛选条件转成数据库枚举码，再由 Mapper 取每个会话的最新一轮摘要行。
        long pageNo = BusinessInputValidator.parsePositiveLong(request.getPageNo(), "pageNo");
        long pageSize = BusinessInputValidator.parsePositiveLong(request.getPageSize(), "pageSize");
        String keyword = BusinessInputValidator.normalizeOptionalText(request.getKeyword());
        String authSessionToken = currentAuthSessionToken();
        Integer chatModeCode = resolveChatModeCode(request.getChatMode());
        Integer turnStatusCode = resolveTurnStatusCode(request.getTurnStatus());
        long totalSize = businessChatDialogueMapper.countSessionPageRows(
                request.getWorkspaceId(),
                authSessionToken,
                keyword,
                chatModeCode,
                turnStatusCode,
                NORMAL_STATUS);
        long offset = (pageNo - 1) * pageSize;

        List<BusinessChatSessionListItemVo> sessionList = totalSize == 0
                ? List.of()
                : businessChatDialogueMapper.selectSessionPageRows(
                                request.getWorkspaceId(),
                                authSessionToken,
                                keyword,
                                chatModeCode,
                                turnStatusCode,
                                NORMAL_STATUS,
                                offset,
                                pageSize)
                        .stream()
                        .map(this::buildSessionListItem)
                        .toList();

        BusinessChatSessionListPageVo pageVo = new BusinessChatSessionListPageVo();
        pageVo.setPageNo(pageNo);
        pageVo.setPageSize(pageSize);
        pageVo.setTotalSize(totalSize);
        pageVo.setTotalPages(totalSize == 0 ? 0 : (totalSize + pageSize - 1) / pageSize);
        pageVo.setSessions(sessionList);
        return pageVo;
    }

    @Override
    public BusinessChatSessionDetailVo getSession(BusinessChatSessionDetailRequest request) {
        String conversationId = BusinessInputValidator.normalizeRequiredText(request.getConversationId(), "conversationId");
        // 详情页以 conversationId 为入口，先定位会话主记录，再拼接摘要和按时间排序的 exchange 明细。
        BusinessChatDialogueData dialogueData = businessChatDialogueMapper.selectOne(
                Wrappers.<BusinessChatDialogueData>lambdaQuery()
                        .eq(BusinessChatDialogueData::getDialogueCode, conversationId)
                        .eq(BusinessChatDialogueData::getWorkspaceId, request.getWorkspaceId())
                        .eq(BusinessChatDialogueData::getAuthSessionToken, currentAuthSessionToken())
                        .eq(BusinessChatDialogueData::getStatus, NORMAL_STATUS)
                        .last("limit 1"));
        if (dialogueData == null) {
            throw new BaseException(
                    BusinessChatErrorCode.CHAT_SESSION_NOT_FOUND,
                    "conversationId was not found: " + conversationId);
        }
        businessChatSessionStateService.activate(conversationId, request.getWorkspaceId(), currentAuthSessionToken());

        BusinessChatMemorySummaryData summaryData = businessChatMemorySummaryMapper.selectOne(
                Wrappers.<BusinessChatMemorySummaryData>lambdaQuery()
                        .eq(BusinessChatMemorySummaryData::getDialogueCode, conversationId)
                        .eq(BusinessChatMemorySummaryData::getWorkspaceId, request.getWorkspaceId())
                        .eq(BusinessChatMemorySummaryData::getStatus, NORMAL_STATUS)
                        .last("limit 1"));
        List<BusinessChatSessionExchangeVo> exchangeList = businessChatExchangeMapper.selectList(
                        Wrappers.<BusinessChatExchangeData>lambdaQuery()
                                .eq(BusinessChatExchangeData::getDialogueCode, conversationId)
                                .eq(BusinessChatExchangeData::getWorkspaceId, request.getWorkspaceId())
                                .eq(BusinessChatExchangeData::getStatus, NORMAL_STATUS)
                                .orderByAsc(BusinessChatExchangeData::getCreateTime)
                                .orderByAsc(BusinessChatExchangeData::getId))
                .stream()
                .map(this::buildSessionExchange)
                .toList();

        BusinessChatSessionDetailVo detailVo = new BusinessChatSessionDetailVo();
        detailVo.setConversationId(dialogueData.getDialogueCode());
        detailVo.setTitle(normalizeStoredTitle(dialogueData.getDialogueTitle()));
        detailVo.setChatMode(BusinessChatMode.fromDatabaseCode(dialogueData.getChatMode()).getValue());
        detailVo.setDialogueStage(BusinessChatDialogueStage.fromDatabaseCode(dialogueData.getDialogueStage())
                .getValue());
        detailVo.setSelectedDocumentId(dialogueData.getSelectedDocumentId() == null
                ? null
                : String.valueOf(dialogueData.getSelectedDocumentId()));
        detailVo.setSelectedDocumentName(dialogueData.getSelectedDocumentName());
        detailVo.setSummaryText(summaryData == null ? null : summaryData.getSummaryText());
        detailVo.setSummaryJson(summaryData == null ? null : readNullableJson(summaryData.getSummaryJson()));
        detailVo.setExchanges(exchangeList);
        return detailVo;
    }

    @Override
    public String getActiveConversationId(String workspaceId, String authSessionToken) {
        return businessChatSessionStateService.getActiveConversationId(workspaceId, authSessionToken);
    }

    @Override
    public BusinessChatExchangeDetailVo getExchangeDetail(BusinessChatExchangeDetailRequest request) {
        String conversationId = BusinessInputValidator.normalizeRequiredText(request.getConversationId(), "conversationId");
        long exchangeId = BusinessInputValidator.parsePositiveLong(request.getExchangeId(), "exchangeId");
        requireVisibleDialogue(conversationId, request.getWorkspaceId());
        BusinessChatExchangeData exchangeData = businessChatExchangeMapper.selectOne(
                Wrappers.<BusinessChatExchangeData>lambdaQuery()
                        .eq(BusinessChatExchangeData::getDialogueCode, conversationId)
                        .eq(BusinessChatExchangeData::getWorkspaceId, request.getWorkspaceId())
                        .eq(BusinessChatExchangeData::getId, exchangeId)
                        .eq(BusinessChatExchangeData::getStatus, NORMAL_STATUS)
                        .last("limit 1"));
        if (exchangeData == null) {
            throw new BaseException(
                    BusinessChatErrorCode.CHAT_SESSION_NOT_FOUND,
                    "exchange was not found: " + exchangeId);
        }
        List<BusinessChatExchangeTraceStageData> stageDataList = businessChatExchangeTraceStageMapper.selectList(
                Wrappers.<BusinessChatExchangeTraceStageData>lambdaQuery()
                        .eq(BusinessChatExchangeTraceStageData::getDialogueCode, conversationId)
                        .eq(BusinessChatExchangeTraceStageData::getWorkspaceId, request.getWorkspaceId())
                        .eq(BusinessChatExchangeTraceStageData::getExchangeId, exchangeId)
                        .eq(BusinessChatExchangeTraceStageData::getStatus, NORMAL_STATUS)
                        .orderByAsc(BusinessChatExchangeTraceStageData::getStageOrder)
                        .orderByAsc(BusinessChatExchangeTraceStageData::getStartTime)
                        .orderByAsc(BusinessChatExchangeTraceStageData::getId));
        List<BusinessChatModelCallTraceData> modelCallDataList = businessChatModelCallTraceMapper.selectList(
                Wrappers.<BusinessChatModelCallTraceData>lambdaQuery()
                        .eq(BusinessChatModelCallTraceData::getDialogueCode, conversationId)
                        .eq(BusinessChatModelCallTraceData::getExchangeId, exchangeId)
                        .eq(BusinessChatModelCallTraceData::getStatus, NORMAL_STATUS)
                        .orderByAsc(BusinessChatModelCallTraceData::getStartTime)
                        .orderByAsc(BusinessChatModelCallTraceData::getId));
        List<BusinessChatToolCallTraceData> toolCallDataList = businessChatToolCallTraceMapper.selectList(
                Wrappers.<BusinessChatToolCallTraceData>lambdaQuery()
                        .eq(BusinessChatToolCallTraceData::getDialogueCode, conversationId)
                        .eq(BusinessChatToolCallTraceData::getExchangeId, exchangeId)
                        .eq(BusinessChatToolCallTraceData::getStatus, NORMAL_STATUS)
                        .orderByAsc(BusinessChatToolCallTraceData::getStartTime)
                        .orderByAsc(BusinessChatToolCallTraceData::getId));

        BusinessChatExchangeDetailVo detailVo = new BusinessChatExchangeDetailVo();
        detailVo.setConversationId(conversationId);
        detailVo.setExchangeId(String.valueOf(exchangeId));
        detailVo.setUserPrompt(exchangeData.getUserPrompt());
        detailVo.setReplyContent(exchangeData.getReplyContent());
        detailVo.setExchangeState(BusinessChatExchangeState.fromDatabaseCode(exchangeData.getExchangeState()).getValue());
        detailVo.setFinishNote(exchangeData.getFinishNote());
        detailVo.setFirstTokenLatencyMs(exchangeData.getFirstTokenLatencyMs());
        detailVo.setTotalLatencyMs(exchangeData.getTotalLatencyMs());
        detailVo.setUsageSummary(buildUsageSummary(modelCallDataList, toolCallDataList));
        detailVo.setStages(stageDataList.stream().map(this::toTraceStageVo).toList());
        detailVo.setModelCalls(modelCallDataList.stream().map(this::toModelCallTraceVo).toList());
        detailVo.setToolCalls(toolCallDataList.stream().map(this::toToolCallTraceVo).toList());
        return detailVo;
    }

    private void requireVisibleDialogue(String conversationId, String workspaceId) {
        BusinessChatDialogueData dialogueData = businessChatDialogueMapper.selectOne(
                Wrappers.<BusinessChatDialogueData>lambdaQuery()
                        .eq(BusinessChatDialogueData::getDialogueCode, conversationId)
                        .eq(BusinessChatDialogueData::getWorkspaceId, workspaceId)
                        .eq(BusinessChatDialogueData::getAuthSessionToken, currentAuthSessionToken())
                        .eq(BusinessChatDialogueData::getStatus, NORMAL_STATUS)
                        .last("limit 1"));
        if (dialogueData == null) {
            throw new BaseException(
                    BusinessChatErrorCode.CHAT_SESSION_NOT_FOUND,
                    "conversationId was not found: " + conversationId);
        }
    }

    private BusinessChatSessionListItemVo buildSessionListItem(BusinessChatSessionListRow row) {
        BusinessChatSessionListItemVo itemVo = new BusinessChatSessionListItemVo();
        itemVo.setConversationId(row.getConversationId());
        itemVo.setTitle(normalizeStoredTitle(row.getTitle()));
        itemVo.setChatMode(BusinessChatMode.fromDatabaseCode(row.getChatModeCode()).getValue());
        itemVo.setTurnStatus(BusinessChatExchangeState.fromDatabaseCode(row.getTurnStatusCode()).getValue());
        itemVo.setLastExchangeId(String.valueOf(row.getLastExchangeId()));
        itemVo.setLastQuestion(row.getLastQuestion());
        itemVo.setLastReply(row.getLastReply());
        itemVo.setUpdateTime(row.getUpdateTime());
        return itemVo;
    }

    private String currentAuthSessionToken() {
        AuthSessionContext session = AuthSessionHolder.required();
        return session.role() == AuthRole.GUEST ? session.token() : "";
    }

    private BusinessChatExchangeUsageSummaryVo buildUsageSummary(
            List<BusinessChatModelCallTraceData> modelCallDataList,
            List<BusinessChatToolCallTraceData> toolCallDataList) {
        BusinessChatExchangeUsageSummaryVo summaryVo = new BusinessChatExchangeUsageSummaryVo();
        int inputTokens = modelCallDataList.stream()
                .map(BusinessChatModelCallTraceData::getInputTokens)
                .mapToInt(value -> value == null ? 0 : value)
                .sum();
        int outputTokens = modelCallDataList.stream()
                .map(BusinessChatModelCallTraceData::getOutputTokens)
                .mapToInt(value -> value == null ? 0 : value)
                .sum();
        int totalTokens = modelCallDataList.stream()
                .map(BusinessChatModelCallTraceData::getTotalTokens)
                .mapToInt(value -> value == null ? 0 : value)
                .sum();
        BigDecimal estimatedCost = modelCallDataList.stream()
                .map(BusinessChatModelCallTraceData::getEstimatedCost)
                .filter(value -> value != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        summaryVo.setInputTokens(inputTokens);
        summaryVo.setOutputTokens(outputTokens);
        summaryVo.setTotalTokens(totalTokens);
        summaryVo.setEstimatedCost(estimatedCost);
        summaryVo.setCurrency(modelCallDataList.stream()
                .map(BusinessChatModelCallTraceData::getCurrency)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse("CNY"));
        summaryVo.setModelCallCount(modelCallDataList.size());
        summaryVo.setModelCallLimit(runtimeProperties.getMaxModelCallsPerRun());
        summaryVo.setToolCallCount(toolCallDataList.size());
        summaryVo.setToolCallLimit(runtimeProperties.getMaxTavilyToolCallsPerRun());
        modelCallDataList.stream()
                .filter(data -> Integer.valueOf(3).equals(data.getCallState()))
                .map(BusinessChatModelCallTraceData::getErrorMessage)
                .filter(StringUtils::hasText)
                .findFirst()
                .ifPresent(errorMessage -> {
                    summaryVo.setLimitTriggered(errorMessage.contains("limit exceeded"));
                    summaryVo.setLimitTriggerReason(errorMessage);
                });
        return summaryVo;
    }

    private BusinessChatExchangeTraceStageVo toTraceStageVo(BusinessChatExchangeTraceStageData data) {
        BusinessChatExchangeTraceStageVo vo = new BusinessChatExchangeTraceStageVo();
        vo.setStageCode(data.getStageCode());
        vo.setStageName(data.getStageName());
        vo.setStageOrder(data.getStageOrder());
        vo.setStageLevel(data.getStageLevel());
        vo.setParentStageId(data.getParentStageId() == null ? null : String.valueOf(data.getParentStageId()));
        vo.setStageState(toTraceState(data.getStageState()));
        vo.setDurationMs(data.getDurationMs());
        vo.setSummaryText(data.getSummaryText());
        vo.setErrorMessage(data.getErrorMessage());
        vo.setSnapshot(readNullableJson(data.getSnapshotJson()));
        vo.setStartTime(data.getStartTime());
        vo.setEndTime(data.getEndTime());
        return vo;
    }

    private BusinessChatModelCallTraceVo toModelCallTraceVo(BusinessChatModelCallTraceData data) {
        BusinessChatModelCallTraceVo vo = new BusinessChatModelCallTraceVo();
        vo.setStageCode(data.getStageCode());
        vo.setStageName(data.getStageName());
        vo.setProvider(data.getProvider());
        vo.setModelName(data.getModelName());
        vo.setCallType(data.getCallType());
        vo.setInputTokens(data.getInputTokens());
        vo.setOutputTokens(data.getOutputTokens());
        vo.setTotalTokens(data.getTotalTokens());
        vo.setEstimatedCost(data.getEstimatedCost());
        vo.setCurrency(data.getCurrency());
        vo.setDurationMs(data.getDurationMs());
        vo.setCallState(toCallState(data.getCallState()));
        vo.setErrorMessage(data.getErrorMessage());
        return vo;
    }

    private BusinessChatToolCallTraceVo toToolCallTraceVo(BusinessChatToolCallTraceData data) {
        BusinessChatToolCallTraceVo vo = new BusinessChatToolCallTraceVo();
        vo.setToolName(data.getToolName());
        vo.setCallState(toCallState(data.getCallState()));
        vo.setDurationMs(data.getDurationMs());
        vo.setErrorMessage(data.getErrorMessage());
        return vo;
    }

    private BusinessChatSessionExchangeVo buildSessionExchange(BusinessChatExchangeData exchangeData) {
        // exchange 落库时 JSON 化的追问和工具痕迹，在这里还原成前端可直接渲染的数组。
        BusinessChatSessionExchangeVo exchangeVo = new BusinessChatSessionExchangeVo();
        exchangeVo.setExchangeId(String.valueOf(exchangeData.getId()));
        exchangeVo.setUserPrompt(exchangeData.getUserPrompt());
        exchangeVo.setReplyContent(exchangeData.getReplyContent());
        exchangeVo.setSourceSnapshotList(readRequiredStringList(
                exchangeData.getSourceSnapshotList(),
                "sourceSnapshotList",
                exchangeData.getId()));
        exchangeVo.setFollowUpSuggestionList(readRequiredStringList(
                exchangeData.getFollowupSuggestionList(),
                "followupSuggestionList",
                exchangeData.getId()));
        exchangeVo.setToolTraceList(readRequiredStringList(
                exchangeData.getToolTraceList(),
                "toolTraceList",
                exchangeData.getId()));
        exchangeVo.setExchangeState(BusinessChatExchangeState.fromDatabaseCode(exchangeData.getExchangeState())
                .getValue());
        exchangeVo.setFinishNote(exchangeData.getFinishNote());
        exchangeVo.setFirstTokenLatencyMs(exchangeData.getFirstTokenLatencyMs());
        exchangeVo.setTotalLatencyMs(exchangeData.getTotalLatencyMs());
        exchangeVo.setCreateTime(exchangeData.getCreateTime());
        return exchangeVo;
    }

    private Integer resolveChatModeCode(String chatMode) {
        String normalizedChatMode = BusinessInputValidator.normalizeRequiredText(chatMode, "chatMode");
        if ("ALL".equalsIgnoreCase(normalizedChatMode)) {
            return null;
        }
        return BusinessChatMode.fromValue(normalizedChatMode).getDatabaseCode();
    }

    private Integer resolveTurnStatusCode(String turnStatus) {
        String normalizedTurnStatus = BusinessInputValidator.normalizeRequiredText(turnStatus, "turnStatus");
        if ("ALL".equalsIgnoreCase(normalizedTurnStatus)) {
            return null;
        }
        return BusinessChatExchangeState.fromValue(normalizedTurnStatus).getDatabaseCode();
    }

    private String toTraceState(Integer state) {
        if (Integer.valueOf(1).equals(state)) {
            return "RUNNING";
        }
        if (Integer.valueOf(2).equals(state)) {
            return "COMPLETED";
        }
        if (Integer.valueOf(3).equals(state)) {
            return "FAILED";
        }
        if (Integer.valueOf(4).equals(state)) {
            return "SKIPPED";
        }
        return "UNKNOWN";
    }

    private String toCallState(Integer state) {
        if (Integer.valueOf(1).equals(state)) {
            return "RUNNING";
        }
        if (Integer.valueOf(2).equals(state)) {
            return "COMPLETED";
        }
        if (Integer.valueOf(3).equals(state)) {
            return "FAILED";
        }
        return "UNKNOWN";
    }

    private String normalizeStoredTitle(String value) {
        String normalizedValue = value == null ? "" : value.strip();
        return StringUtils.hasText(normalizedValue) ? normalizedValue : "";
    }

    private List<String> readRequiredStringList(String json, String fieldName, Long exchangeId) {
        if (!StringUtils.hasText(json)) {
            throw new IllegalStateException(fieldName + " is empty for exchangeId=" + exchangeId);
        }
        try {
            return objectMapper.readValue(json, STRING_LIST_TYPE);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException(
                    "Failed to deserialize %s for exchangeId=%s".formatted(fieldName, exchangeId),
                    exception);
        }
    }

    private Object readNullableJson(String json) {
        if (!StringUtils.hasText(json)) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to deserialize summaryJson.", exception);
        }
    }
}
