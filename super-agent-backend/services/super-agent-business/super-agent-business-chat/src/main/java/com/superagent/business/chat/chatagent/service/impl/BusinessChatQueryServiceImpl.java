package com.superagent.business.chat.chatagent.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superagent.business.chat.chatagent.data.BusinessChatDialogueData;
import com.superagent.business.chat.chatagent.data.BusinessChatExchangeData;
import com.superagent.business.chat.chatagent.data.BusinessChatMemorySummaryData;
import com.superagent.business.chat.chatagent.dto.BusinessChatSessionDetailRequest;
import com.superagent.business.chat.chatagent.dto.BusinessChatSessionListRequest;
import com.superagent.business.chat.chatagent.mapper.BusinessChatDialogueMapper;
import com.superagent.business.chat.chatagent.mapper.BusinessChatExchangeMapper;
import com.superagent.business.chat.chatagent.mapper.BusinessChatMemorySummaryMapper;
import com.superagent.business.chat.chatagent.model.BusinessChatDialogueStage;
import com.superagent.business.chat.chatagent.model.BusinessChatExchangeState;
import com.superagent.business.chat.chatagent.model.BusinessChatMode;
import com.superagent.business.chat.chatagent.model.BusinessChatSessionListRow;
import com.superagent.business.chat.chatagent.service.BusinessChatErrorCode;
import com.superagent.business.chat.chatagent.service.BusinessChatQueryService;
import com.superagent.business.chat.chatagent.vo.BusinessChatSessionDetailVo;
import com.superagent.business.chat.chatagent.vo.BusinessChatSessionExchangeVo;
import com.superagent.business.chat.chatagent.vo.BusinessChatSessionListItemVo;
import com.superagent.business.chat.chatagent.vo.BusinessChatSessionListPageVo;
import com.superagent.common.frame.enums.BaseCode;
import com.superagent.common.frame.exception.BaseException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class BusinessChatQueryServiceImpl implements BusinessChatQueryService {

    private static final int NORMAL_STATUS = 1;

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private final BusinessChatDialogueMapper businessChatDialogueMapper;

    private final BusinessChatExchangeMapper businessChatExchangeMapper;

    private final BusinessChatMemorySummaryMapper businessChatMemorySummaryMapper;

    private final ObjectMapper objectMapper;

    @Override
    public BusinessChatSessionListPageVo listSessionsPage(BusinessChatSessionListRequest request) {
        // 列表查询把前端筛选条件转成数据库枚举码，再由 Mapper 取每个会话的最新一轮摘要行。
        long pageNo = parsePositiveLong(request.getPageNo(), "pageNo");
        long pageSize = parsePositiveLong(request.getPageSize(), "pageSize");
        String keyword = normalizeOptionalText(request.getKeyword());
        Integer chatModeCode = resolveChatModeCode(request.getChatMode());
        Integer turnStatusCode = resolveTurnStatusCode(request.getTurnStatus());
        long totalSize = businessChatDialogueMapper.countSessionPageRows(
                keyword,
                chatModeCode,
                turnStatusCode,
                NORMAL_STATUS);
        long offset = (pageNo - 1) * pageSize;

        List<BusinessChatSessionListItemVo> sessionList = totalSize == 0
                ? List.of()
                : businessChatDialogueMapper.selectSessionPageRows(
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
        String conversationId = normalizeRequiredText(request.getConversationId(), "conversationId");
        // 详情页以 conversationId 为入口，先定位会话主记录，再拼接摘要和按时间排序的 exchange 明细。
        BusinessChatDialogueData dialogueData = businessChatDialogueMapper.selectOne(
                Wrappers.<BusinessChatDialogueData>lambdaQuery()
                        .eq(BusinessChatDialogueData::getDialogueCode, conversationId)
                        .eq(BusinessChatDialogueData::getStatus, NORMAL_STATUS)
                        .last("limit 1"));
        if (dialogueData == null) {
            throw new BaseException(
                    BusinessChatErrorCode.CHAT_SESSION_NOT_FOUND,
                    "conversationId was not found: " + conversationId);
        }

        BusinessChatMemorySummaryData summaryData = businessChatMemorySummaryMapper.selectOne(
                Wrappers.<BusinessChatMemorySummaryData>lambdaQuery()
                        .eq(BusinessChatMemorySummaryData::getDialogueCode, conversationId)
                        .eq(BusinessChatMemorySummaryData::getStatus, NORMAL_STATUS)
                        .last("limit 1"));
        List<BusinessChatSessionExchangeVo> exchangeList = businessChatExchangeMapper.selectList(
                        Wrappers.<BusinessChatExchangeData>lambdaQuery()
                                .eq(BusinessChatExchangeData::getDialogueCode, conversationId)
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
        detailVo.setSelectedDocumentId(dialogueData.getSelectedDocumentId());
        detailVo.setSelectedDocumentName(dialogueData.getSelectedDocumentName());
        detailVo.setSummaryText(summaryData == null ? null : summaryData.getSummaryText());
        detailVo.setSummaryJson(summaryData == null ? null : readNullableJson(summaryData.getSummaryJson()));
        detailVo.setExchanges(exchangeList);
        return detailVo;
    }

    private BusinessChatSessionListItemVo buildSessionListItem(BusinessChatSessionListRow row) {
        BusinessChatSessionListItemVo itemVo = new BusinessChatSessionListItemVo();
        itemVo.setConversationId(row.getConversationId());
        itemVo.setTitle(normalizeStoredTitle(row.getTitle()));
        itemVo.setChatMode(BusinessChatMode.fromDatabaseCode(row.getChatModeCode()).getValue());
        itemVo.setTurnStatus(BusinessChatExchangeState.fromDatabaseCode(row.getTurnStatusCode()).getValue());
        itemVo.setLastExchangeId(row.getLastExchangeId());
        itemVo.setLastQuestion(row.getLastQuestion());
        itemVo.setLastReply(row.getLastReply());
        itemVo.setUpdateTime(row.getUpdateTime());
        return itemVo;
    }

    private BusinessChatSessionExchangeVo buildSessionExchange(BusinessChatExchangeData exchangeData) {
        // exchange 落库时 JSON 化的追问和工具痕迹，在这里还原成前端可直接渲染的数组。
        BusinessChatSessionExchangeVo exchangeVo = new BusinessChatSessionExchangeVo();
        exchangeVo.setExchangeId(exchangeData.getId());
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
        String normalizedChatMode = normalizeRequiredText(chatMode, "chatMode");
        if ("ALL".equalsIgnoreCase(normalizedChatMode)) {
            return null;
        }
        return BusinessChatMode.fromValue(normalizedChatMode).getDatabaseCode();
    }

    private Integer resolveTurnStatusCode(String turnStatus) {
        String normalizedTurnStatus = normalizeRequiredText(turnStatus, "turnStatus");
        if ("ALL".equalsIgnoreCase(normalizedTurnStatus)) {
            return null;
        }
        return BusinessChatExchangeState.fromValue(normalizedTurnStatus).getDatabaseCode();
    }

    private long parsePositiveLong(String value, String fieldName) {
        String normalizedValue = normalizeRequiredText(value, fieldName);
        try {
            long parsedValue = Long.parseLong(normalizedValue);
            if (parsedValue <= 0) {
                throw new BaseException(BaseCode.INVALID_PARAMETER, fieldName + " must be a positive integer");
            }
            return parsedValue;
        } catch (NumberFormatException exception) {
            throw new BaseException(BaseCode.INVALID_PARAMETER, fieldName + " must be a positive integer");
        }
    }

    private String normalizeOptionalText(String value) {
        String normalizedValue = value == null ? null : value.strip();
        return StringUtils.hasText(normalizedValue) ? normalizedValue : null;
    }

    private String normalizeRequiredText(String value, String fieldName) {
        String normalizedValue = value == null ? null : value.strip();
        if (!StringUtils.hasText(normalizedValue)) {
            throw new BaseException(BaseCode.INVALID_PARAMETER, fieldName + " must not be blank");
        }
        return normalizedValue;
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
