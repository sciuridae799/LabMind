package com.labmind.business.chat.chatagent.execution.impl;

import com.labmind.business.chat.chatagent.execution.model.BusinessChatModelApiConfigSnapshot;
import com.labmind.business.chat.chatagent.runtime.BusinessChatRuntimeContext;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.stereotype.Component;

@Component
class BusinessChatModelBusinessLogger {

    private static final Logger log = LoggerFactory.getLogger("BUSINESS_MODEL_CALL");

    String nextCallId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    void logCompleted(
            String callId,
            BusinessChatRuntimeContext runtimeContext,
            BusinessChatModelApiConfigSnapshot modelConfig,
            String callType,
            BusinessChatModelPrompt prompt,
            String responseContent,
            Usage usage,
            long startNanoTime,
            int streamChunkCount) {
        logCompleted(
                callId,
                runtimeContext,
                modelConfig,
                callType,
                prompt,
                responseContent,
                usage,
                startNanoTime,
                streamChunkCount,
                null,
                null);
    }

    void logCompleted(
            String callId,
            BusinessChatRuntimeContext runtimeContext,
            BusinessChatModelApiConfigSnapshot modelConfig,
            String callType,
            BusinessChatModelPrompt prompt,
            String responseContent,
            Usage usage,
            long startNanoTime,
            int streamChunkCount,
            String stageCode,
            String stageName) {
        log.info(buildLogMessage(
                callId,
                runtimeContext,
                modelConfig,
                callType,
                "COMPLETED",
                prompt,
                responseContent,
                usage,
                null,
                startNanoTime,
                streamChunkCount,
                stageCode,
                stageName));
    }

    void logFailed(
            String callId,
            BusinessChatRuntimeContext runtimeContext,
            BusinessChatModelApiConfigSnapshot modelConfig,
            String callType,
            BusinessChatModelPrompt prompt,
            String partialResponseContent,
            Throwable error,
            long startNanoTime,
            int streamChunkCount) {
        logFailed(
                callId,
                runtimeContext,
                modelConfig,
                callType,
                prompt,
                partialResponseContent,
                error,
                startNanoTime,
                streamChunkCount,
                null,
                null);
    }

    void logFailed(
            String callId,
            BusinessChatRuntimeContext runtimeContext,
            BusinessChatModelApiConfigSnapshot modelConfig,
            String callType,
            BusinessChatModelPrompt prompt,
            String partialResponseContent,
            Throwable error,
            long startNanoTime,
            int streamChunkCount,
            String stageCode,
            String stageName) {
        log.info(buildLogMessage(
                callId,
                runtimeContext,
                modelConfig,
                callType,
                "FAILED",
                prompt,
                partialResponseContent,
                null,
                error,
                startNanoTime,
                streamChunkCount,
                stageCode,
                stageName));
    }

    Usage extractUsage(ChatResponse response) {
        if (response == null || response.getMetadata() == null) {
            return null;
        }
        return response.getMetadata().getUsage();
    }

    private String buildLogMessage(
            String callId,
            BusinessChatRuntimeContext runtimeContext,
            BusinessChatModelApiConfigSnapshot modelConfig,
            String callType,
            String state,
            BusinessChatModelPrompt prompt,
            String responseContent,
            Usage usage,
            Throwable error,
            long startNanoTime,
            int streamChunkCount,
            String stageCode,
            String stageName) {
        StringBuilder builder = new StringBuilder();
        builder.append("\n========== MODEL BUSINESS CALL ==========\n");
        builder.append("callId=").append(callId).append("\n");
        builder.append("state=").append(state).append("\n");
        builder.append("callType=").append(callType).append("\n");
        builder.append("provider=").append(modelConfig.provider().getValue()).append("\n");
        builder.append("baseUrl=").append(nullToEmpty(modelConfig.baseUrl())).append("\n");
        builder.append("modelName=").append(modelConfig.modelName()).append("\n");
        builder.append("displayName=").append(nullToEmpty(modelConfig.displayName())).append("\n");
        builder.append("durationMs=").append(Duration.ofNanos(System.nanoTime() - startNanoTime).toMillis()).append("\n");
        builder.append("streamChunkCount=").append(streamChunkCount).append("\n");
        appendUsage(builder, usage);
        appendRuntimeContext(builder, runtimeContext, stageCode, stageName);
        if (error != null) {
            builder.append("errorClass=").append(error.getClass().getName()).append("\n");
            builder.append("errorMessage=").append(nullToEmpty(error.getMessage())).append("\n");
        }
        builder.append("\n----- SYSTEM PROMPT -----\n");
        builder.append(nullToEmpty(prompt.systemPrompt())).append("\n");
        builder.append("\n----- USER PROMPT -----\n");
        builder.append(nullToEmpty(prompt.userPrompt())).append("\n");
        builder.append("\n----- MODEL OUTPUT");
        if (error != null) {
            builder.append(" PARTIAL");
        }
        builder.append(" -----\n");
        builder.append(nullToEmpty(responseContent)).append("\n");
        builder.append("======== END MODEL BUSINESS CALL ========\n");
        return builder.toString();
    }

    private void appendUsage(StringBuilder builder, Usage usage) {
        if (usage == null) {
            builder.append("promptTokens=\n");
            builder.append("completionTokens=\n");
            builder.append("totalTokens=\n");
            return;
        }
        builder.append("promptTokens=").append(usage.getPromptTokens()).append("\n");
        builder.append("completionTokens=").append(usage.getCompletionTokens()).append("\n");
        builder.append("totalTokens=").append(usage.getTotalTokens()).append("\n");
    }

    private void appendRuntimeContext(
            StringBuilder builder,
            BusinessChatRuntimeContext runtimeContext,
            String stageCode,
            String stageName) {
        if (runtimeContext == null) {
            builder.append("traceId=\n");
            builder.append("conversationId=\n");
            builder.append("exchangeId=\n");
            builder.append("chatMode=\n");
            builder.append("stageCode=\n");
            builder.append("stageName=\n");
            return;
        }
        builder.append("traceId=").append(runtimeContext.getTaskInfo().traceId()).append("\n");
        builder.append("conversationId=").append(runtimeContext.getTaskInfo().conversationId()).append("\n");
        builder.append("exchangeId=").append(runtimeContext.getTaskInfo().exchangeId()).append("\n");
        builder.append("chatMode=").append(runtimeContext.getTaskInfo().chatMode().getValue()).append("\n");
        builder.append("stageCode=").append(nullToEmpty(resolveStageCode(runtimeContext, stageCode))).append("\n");
        builder.append("stageName=").append(nullToEmpty(resolveStageName(runtimeContext, stageName))).append("\n");
    }

    private String resolveStageCode(BusinessChatRuntimeContext runtimeContext, String stageCode) {
        return stageCode == null ? runtimeContext.getCurrentTraceStageCode() : stageCode;
    }

    private String resolveStageName(BusinessChatRuntimeContext runtimeContext, String stageName) {
        return stageName == null ? runtimeContext.getCurrentTraceStageName() : stageName;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
