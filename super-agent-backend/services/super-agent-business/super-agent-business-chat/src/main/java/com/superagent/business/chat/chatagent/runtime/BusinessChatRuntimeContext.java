package com.superagent.business.chat.chatagent.runtime;

import com.superagent.business.chat.chatagent.model.BusinessChatExecutionPlan;
import com.superagent.business.chat.chatagent.model.BusinessChatIntentAnalysis;
import com.superagent.business.chat.chatagent.model.BusinessChatTaskInfo;
import com.superagent.business.chat.chatagent.vo.BusinessChatStreamEvent;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import lombok.Getter;
import lombok.Setter;
import reactor.core.publisher.Sinks;

public class BusinessChatRuntimeContext {

    // 输入快照：StartPlan -> TaskInfo -> RuntimeContext。
    @Getter
    private final BusinessChatTaskInfo taskInfo;

    // 输出通道：RuntimeContext -> SSE。
    @Getter
    private final Sinks.Many<BusinessChatStreamEvent> outputChannel;

    // 正文缓冲：TEXT_DELTA -> replyContent。
    private final StringBuilder replyContentBuilder;

    // 伴随输出：reasoning/source/followUp/toolTrace -> FinalizedTurn。
    @Getter
    private final List<String> reasoningNoteList;

    @Getter
    private final List<String> sourceSnapshotList;

    @Getter
    private final List<String> followUpSuggestionList;

    @Getter
    private final List<String> toolTraceList;

    private final AtomicBoolean firstTokenDelivered;

    private final AtomicBoolean finalized;

    private final AtomicBoolean outputClosed;

    private final AtomicLong firstTokenLatencyMs;

    @Getter
    @Setter
    private volatile BusinessChatIntentAnalysis intentAnalysis;

    @Getter
    @Setter
    private volatile BusinessChatExecutionPlan executionPlan;

    public BusinessChatRuntimeContext(BusinessChatTaskInfo taskInfo, Sinks.Many<BusinessChatStreamEvent> outputChannel) {
        this.taskInfo = taskInfo;
        this.outputChannel = outputChannel;
        this.replyContentBuilder = new StringBuilder();
        this.reasoningNoteList = new CopyOnWriteArrayList<>();
        this.sourceSnapshotList = new CopyOnWriteArrayList<>();
        this.followUpSuggestionList = new CopyOnWriteArrayList<>();
        this.toolTraceList = new CopyOnWriteArrayList<>();
        this.firstTokenDelivered = new AtomicBoolean(false);
        this.finalized = new AtomicBoolean(false);
        this.outputClosed = new AtomicBoolean(false);
        this.firstTokenLatencyMs = new AtomicLong(-1L);
    }

    public synchronized void appendReplyContent(String textDelta) {
        replyContentBuilder.append(textDelta);
    }

    public synchronized String getReplyContent() {
        return replyContentBuilder.toString();
    }

    public boolean markFirstTokenDelivered() {
        return firstTokenDelivered.compareAndSet(false, true);
    }

    public long getFirstTokenLatencyMs() {
        return firstTokenLatencyMs.get();
    }

    public void setFirstTokenLatencyMs(long latencyMs) {
        firstTokenLatencyMs.compareAndSet(-1L, latencyMs);
    }

    public boolean markFinalized() {
        return finalized.compareAndSet(false, true);
    }

    public boolean isOutputClosed() {
        return outputClosed.get();
    }

    public void markOutputClosed() {
        outputClosed.set(true);
    }

    public synchronized BusinessChatFinalizedTurn freezeFinalizedTurn() {
        return new BusinessChatFinalizedTurn(
                taskInfo,
                replyContentBuilder.toString(),
                List.copyOf(reasoningNoteList),
                List.copyOf(sourceSnapshotList),
                List.copyOf(followUpSuggestionList),
                List.copyOf(toolTraceList),
                intentAnalysis,
                executionPlan,
                getFirstTokenLatencyMs(),
                System.currentTimeMillis() - taskInfo.startAtEpochMillis());
    }

}
