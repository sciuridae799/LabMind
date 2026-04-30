package com.superagent.business.chat.chatagent.trace;

import com.superagent.business.chat.chatagent.persistence.data.BusinessChatModelCallTraceData;
import com.superagent.business.chat.chatagent.persistence.data.BusinessChatToolCallTraceData;
import com.superagent.business.chat.chatagent.persistence.mapper.BusinessChatModelCallTraceMapper;
import com.superagent.business.chat.chatagent.persistence.mapper.BusinessChatToolCallTraceMapper;
import com.superagent.business.chat.chatagent.execution.model.BusinessChatModelApiConfigSnapshot;
import com.superagent.business.chat.chatagent.execution.model.BusinessChatModelPricing;
import com.superagent.business.chat.chatagent.execution.model.BusinessChatModelProvider;
import com.superagent.business.chat.chatagent.runtime.BusinessChatRuntimeContext;
import com.superagent.idgenerator.toolkit.SnowflakeIdGenerator;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BusinessChatUsageTraceServiceImpl implements BusinessChatUsageTraceService {

    private static final int NORMAL_STATUS = 1;

    private static final int CALL_RUNNING = 1;

    private static final int CALL_COMPLETED = 2;

    private static final int CALL_FAILED = 3;

    private static final String MODEL_STAGE_CODE = "MODEL_CALL";

    private static final String MODEL_STAGE_NAME = "模型调用";

    private final BusinessChatModelCallTraceMapper modelCallTraceMapper;

    private final BusinessChatToolCallTraceMapper toolCallTraceMapper;

    private final SnowflakeIdGenerator snowflakeIdGenerator;

    @Override
    @Transactional
    public Long startModelCall(
            BusinessChatRuntimeContext runtimeContext,
            BusinessChatModelApiConfigSnapshot modelConfig,
            String callType) {
        BusinessChatModelCallTraceData data = new BusinessChatModelCallTraceData();
        data.setId(snowflakeIdGenerator.nextId());
        data.setDialogueCode(runtimeContext.getTaskInfo().conversationId());
        data.setExchangeId(runtimeContext.getTaskInfo().exchangeId());
        data.setTraceId(runtimeContext.getTaskInfo().traceId());
        data.setStageCode(MODEL_STAGE_CODE);
        data.setStageName(MODEL_STAGE_NAME);
        data.setProvider(modelConfig.provider().getValue());
        data.setBaseUrl(modelConfig.baseUrl());
        data.setModelName(modelConfig.modelName());
        data.setCallType(callType);
        data.setInputTokens(0);
        data.setOutputTokens(0);
        data.setTotalTokens(0);
        data.setInputTokenUnitPrice(modelConfig.inputTokenUnitPrice());
        data.setOutputTokenUnitPrice(modelConfig.outputTokenUnitPrice());
        data.setPriceUnitTokens(modelConfig.priceUnitTokens());
        data.setCurrency(modelConfig.currency());
        data.setEstimatedCost(BigDecimal.ZERO);
        data.setCallState(CALL_RUNNING);
        data.setStartTime(LocalDateTime.now());
        data.setStatus(NORMAL_STATUS);
        modelCallTraceMapper.insert(data);
        return data.getId();
    }

    @Override
    @Transactional
    public void completeModelCall(Long traceId, Usage usage) {
        if (traceId == null) {
            return;
        }
        BusinessChatModelCallTraceData existing = requireModelTrace(traceId);
        LocalDateTime endTime = LocalDateTime.now();
        int inputTokens = normalizeTokenCount(usage == null ? null : usage.getPromptTokens());
        int outputTokens = normalizeTokenCount(usage == null ? null : usage.getCompletionTokens());
        int totalTokens = normalizeTokenCount(usage == null ? null : usage.getTotalTokens());
        if (totalTokens == 0) {
            totalTokens = inputTokens + outputTokens;
        }
        BusinessChatModelCallTraceData data = new BusinessChatModelCallTraceData();
        data.setId(traceId);
        data.setInputTokens(inputTokens);
        data.setOutputTokens(outputTokens);
        data.setTotalTokens(totalTokens);
        BusinessChatModelPricing.PriceQuote priceQuote = BusinessChatModelPricing.quote(
                BusinessChatModelProvider.fromValue(existing.getProvider()),
                existing.getBaseUrl(),
                existing.getModelName(),
                existing.getCallType(),
                inputTokens,
                outputTokens);
        data.setInputTokenUnitPrice(priceQuote.inputTokenUnitPrice());
        data.setOutputTokenUnitPrice(priceQuote.outputTokenUnitPrice());
        data.setPriceUnitTokens(priceQuote.priceUnitTokens());
        data.setCurrency(priceQuote.currency());
        data.setEstimatedCost(priceQuote.estimatedCost());
        data.setCallState(CALL_COMPLETED);
        data.setEndTime(endTime);
        data.setDurationMs(Duration.between(existing.getStartTime(), endTime).toMillis());
        data.setErrorMessage(null);
        modelCallTraceMapper.updateById(data);
    }

    @Override
    @Transactional
    public void failModelCall(Long traceId, Throwable error) {
        if (traceId == null) {
            return;
        }
        BusinessChatModelCallTraceData existing = requireModelTrace(traceId);
        LocalDateTime endTime = LocalDateTime.now();
        BusinessChatModelCallTraceData data = new BusinessChatModelCallTraceData();
        data.setId(traceId);
        data.setCallState(CALL_FAILED);
        data.setEndTime(endTime);
        data.setDurationMs(Duration.between(existing.getStartTime(), endTime).toMillis());
        data.setErrorMessage(error.getMessage());
        modelCallTraceMapper.updateById(data);
    }

    @Override
    @Transactional
    public Long startToolCall(BusinessChatRuntimeContext runtimeContext, String toolName) {
        BusinessChatToolCallTraceData data = new BusinessChatToolCallTraceData();
        data.setId(snowflakeIdGenerator.nextId());
        data.setDialogueCode(runtimeContext.getTaskInfo().conversationId());
        data.setExchangeId(runtimeContext.getTaskInfo().exchangeId());
        data.setTraceId(runtimeContext.getTaskInfo().traceId());
        data.setToolName(toolName);
        data.setCallState(CALL_RUNNING);
        data.setStartTime(LocalDateTime.now());
        data.setStatus(NORMAL_STATUS);
        toolCallTraceMapper.insert(data);
        return data.getId();
    }

    @Override
    @Transactional
    public void completeToolCall(Long traceId) {
        if (traceId == null) {
            return;
        }
        BusinessChatToolCallTraceData existing = requireToolTrace(traceId);
        LocalDateTime endTime = LocalDateTime.now();
        BusinessChatToolCallTraceData data = new BusinessChatToolCallTraceData();
        data.setId(traceId);
        data.setCallState(CALL_COMPLETED);
        data.setEndTime(endTime);
        data.setDurationMs(Duration.between(existing.getStartTime(), endTime).toMillis());
        data.setErrorMessage(null);
        toolCallTraceMapper.updateById(data);
    }

    @Override
    @Transactional
    public void failToolCall(Long traceId, Throwable error) {
        if (traceId == null) {
            return;
        }
        BusinessChatToolCallTraceData existing = requireToolTrace(traceId);
        LocalDateTime endTime = LocalDateTime.now();
        BusinessChatToolCallTraceData data = new BusinessChatToolCallTraceData();
        data.setId(traceId);
        data.setCallState(CALL_FAILED);
        data.setEndTime(endTime);
        data.setDurationMs(Duration.between(existing.getStartTime(), endTime).toMillis());
        data.setErrorMessage(error.getMessage());
        toolCallTraceMapper.updateById(data);
    }

    private BusinessChatModelCallTraceData requireModelTrace(Long traceId) {
        BusinessChatModelCallTraceData data = modelCallTraceMapper.selectById(traceId);
        if (data == null) {
            throw new IllegalStateException("model call trace does not exist: " + traceId);
        }
        return data;
    }

    private BusinessChatToolCallTraceData requireToolTrace(Long traceId) {
        BusinessChatToolCallTraceData data = toolCallTraceMapper.selectById(traceId);
        if (data == null) {
            throw new IllegalStateException("tool call trace does not exist: " + traceId);
        }
        return data;
    }

    private int normalizeTokenCount(Integer value) {
        return value == null ? 0 : value;
    }

}
