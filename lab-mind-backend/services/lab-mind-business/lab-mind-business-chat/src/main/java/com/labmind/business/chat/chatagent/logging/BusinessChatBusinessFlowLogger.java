package com.labmind.business.chat.chatagent.logging;

import com.labmind.business.chat.chatagent.orchestration.model.BusinessChatAgentStep;
import com.labmind.business.chat.chatagent.orchestration.model.BusinessChatExecutionPlan;
import com.labmind.business.chat.chatagent.orchestration.model.BusinessChatStartPlan;
import com.labmind.business.chat.chatagent.runtime.BusinessChatFinalizedTurn;
import com.labmind.business.chat.chatagent.runtime.BusinessChatRuntimeContext;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class BusinessChatBusinessFlowLogger {

    private static final Logger log = LoggerFactory.getLogger("BUSINESS_CHAT_FLOW");

    public void logStart(BusinessChatStartPlan startPlan) {
        StringBuilder builder = new StringBuilder();
        builder.append("\n========== CHAT BUSINESS FLOW ==========\n");
        builder.append("event=START\n");
        builder.append("traceId=").append(startPlan.traceId()).append("\n");
        builder.append("conversationId=").append(startPlan.conversationId()).append("\n");
        builder.append("chatMode=").append(startPlan.chatMode().getValue()).append("\n");
        builder.append("modelName=").append(startPlan.modelConfig().modelName()).append("\n");
        builder.append("selectedDocumentId=").append(nullToEmpty(startPlan.selectedDocumentId())).append("\n");
        builder.append("selectedDocumentName=").append(nullToEmpty(startPlan.selectedDocumentName())).append("\n");
        builder.append("\n----- QUESTION -----\n");
        builder.append(startPlan.question()).append("\n");
        builder.append("======== END CHAT BUSINESS FLOW ========\n");
        log.info(builder.toString());
    }

    public void logExecutionPlan(BusinessChatRuntimeContext runtimeContext, BusinessChatExecutionPlan executionPlan) {
        StringBuilder builder = new StringBuilder();
        builder.append("\n========== CHAT BUSINESS FLOW ==========\n");
        builder.append("event=EXECUTION_PLAN\n");
        appendRuntime(builder, runtimeContext);
        builder.append("executionMode=").append(executionPlan.executionMode().getValue()).append("\n");
        builder.append("executionModel=").append(executionPlan.executionModel()).append("\n");
        builder.append("intentLabel=").append(executionPlan.intentLabel()).append("\n");
        builder.append("intentReason=").append(executionPlan.intentReason()).append("\n");
        builder.append("knowledgeRoute=").append(nullToEmpty(executionPlan.knowledgeRoute())).append("\n");
        builder.append("shortCircuit=").append(executionPlan.shortCircuit()).append("\n");
        builder.append("agentStepList=").append(renderAgentStepList(executionPlan)).append("\n");
        builder.append("\n----- ORIGINAL QUESTION -----\n");
        builder.append(nullToEmpty(executionPlan.originalQuestion())).append("\n");
        builder.append("\n----- REWRITTEN QUESTION -----\n");
        builder.append(nullToEmpty(executionPlan.rewrittenQuestion())).append("\n");
        builder.append("======== END CHAT BUSINESS FLOW ========\n");
        log.info(builder.toString());
    }

    public void logFinished(BusinessChatFinalizedTurn finalizedTurn) {
        StringBuilder builder = new StringBuilder();
        builder.append("\n========== CHAT BUSINESS FLOW ==========\n");
        builder.append("event=FINISHED\n");
        appendTaskInfo(builder, finalizedTurn);
        builder.append("replyLength=").append(finalizedTurn.replyContent().length()).append("\n");
        builder.append("sourceCount=").append(finalizedTurn.sourceSnapshotList().size()).append("\n");
        builder.append("followUpCount=").append(finalizedTurn.followUpSuggestionList().size()).append("\n");
        builder.append("firstTokenLatencyMs=").append(finalizedTurn.firstTokenLatencyMs()).append("\n");
        builder.append("totalLatencyMs=").append(finalizedTurn.totalLatencyMs()).append("\n");
        builder.append("======== END CHAT BUSINESS FLOW ========\n");
        log.info(builder.toString());
    }

    public void logFailed(BusinessChatRuntimeContext runtimeContext, Throwable error) {
        StringBuilder builder = new StringBuilder();
        builder.append("\n========== CHAT BUSINESS FLOW ==========\n");
        builder.append("event=FAILED\n");
        appendRuntime(builder, runtimeContext);
        builder.append("replyLength=").append(runtimeContext.getReplyContent().length()).append("\n");
        builder.append("errorClass=").append(error.getClass().getName()).append("\n");
        builder.append("errorMessage=").append(nullToEmpty(error.getMessage())).append("\n");
        builder.append("======== END CHAT BUSINESS FLOW ========\n");
        log.info(builder.toString());
    }

    public void logStopped(BusinessChatRuntimeContext runtimeContext) {
        StringBuilder builder = new StringBuilder();
        builder.append("\n========== CHAT BUSINESS FLOW ==========\n");
        builder.append("event=STOPPED\n");
        appendRuntime(builder, runtimeContext);
        builder.append("replyLength=").append(runtimeContext.getReplyContent().length()).append("\n");
        builder.append("======== END CHAT BUSINESS FLOW ========\n");
        log.info(builder.toString());
    }

    private String renderAgentStepList(BusinessChatExecutionPlan executionPlan) {
        return executionPlan.agentStepList().stream()
                .map(this::renderAgentStep)
                .collect(Collectors.joining(" -> "));
    }

    private String renderAgentStep(BusinessChatAgentStep step) {
        return step.stageCode() + "/" + step.stageName() + "/answerProducer=" + step.answerProducer();
    }

    private void appendRuntime(StringBuilder builder, BusinessChatRuntimeContext runtimeContext) {
        builder.append("traceId=").append(runtimeContext.getTaskInfo().traceId()).append("\n");
        builder.append("conversationId=").append(runtimeContext.getTaskInfo().conversationId()).append("\n");
        builder.append("exchangeId=").append(runtimeContext.getTaskInfo().exchangeId()).append("\n");
        builder.append("chatMode=").append(runtimeContext.getTaskInfo().chatMode().getValue()).append("\n");
    }

    private void appendTaskInfo(StringBuilder builder, BusinessChatFinalizedTurn finalizedTurn) {
        builder.append("traceId=").append(finalizedTurn.taskInfo().traceId()).append("\n");
        builder.append("conversationId=").append(finalizedTurn.taskInfo().conversationId()).append("\n");
        builder.append("exchangeId=").append(finalizedTurn.taskInfo().exchangeId()).append("\n");
        builder.append("chatMode=").append(finalizedTurn.taskInfo().chatMode().getValue()).append("\n");
    }

    private String nullToEmpty(Object value) {
        return value == null ? "" : String.valueOf(value);
    }
}
