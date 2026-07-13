package com.labmind.business.chat.chatagent.logging;

import com.labmind.business.chat.chatagent.runtime.BusinessChatRuntimeContext;
import java.time.Duration;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class BusinessChatToolBusinessLogger {

    private static final Logger log = LoggerFactory.getLogger("BUSINESS_TOOL_CALL");

    public String nextCallId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public void logCompleted(
            String callId,
            BusinessChatRuntimeContext runtimeContext,
            String toolName,
            Object request,
            Object response,
            long startNanoTime) {
        log.info(buildLogMessage(callId, runtimeContext, toolName, "COMPLETED", request, response, null, startNanoTime));
    }

    public void logFailed(
            String callId,
            BusinessChatRuntimeContext runtimeContext,
            String toolName,
            Object request,
            Throwable error,
            long startNanoTime) {
        log.info(buildLogMessage(callId, runtimeContext, toolName, "FAILED", request, null, error, startNanoTime));
    }

    private String buildLogMessage(
            String callId,
            BusinessChatRuntimeContext runtimeContext,
            String toolName,
            String state,
            Object request,
            Object response,
            Throwable error,
            long startNanoTime) {
        StringBuilder builder = new StringBuilder();
        builder.append("\n========== TOOL BUSINESS CALL ==========\n");
        builder.append("callId=").append(callId).append("\n");
        builder.append("state=").append(state).append("\n");
        builder.append("toolName=").append(toolName).append("\n");
        builder.append("durationMs=").append(Duration.ofNanos(System.nanoTime() - startNanoTime).toMillis()).append("\n");
        builder.append("traceId=").append(runtimeContext.getTaskInfo().traceId()).append("\n");
        builder.append("conversationId=").append(runtimeContext.getTaskInfo().conversationId()).append("\n");
        builder.append("exchangeId=").append(runtimeContext.getTaskInfo().exchangeId()).append("\n");
        builder.append("chatMode=").append(runtimeContext.getTaskInfo().chatMode().getValue()).append("\n");
        if (error != null) {
            builder.append("errorClass=").append(error.getClass().getName()).append("\n");
            builder.append("errorMessage=").append(nullToEmpty(error.getMessage())).append("\n");
        }
        builder.append("\n----- TOOL INPUT -----\n");
        builder.append(nullToEmpty(request)).append("\n");
        builder.append("\n----- TOOL OUTPUT -----\n");
        builder.append(nullToEmpty(response)).append("\n");
        builder.append("======== END TOOL BUSINESS CALL ========\n");
        return builder.toString();
    }

    private String nullToEmpty(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
