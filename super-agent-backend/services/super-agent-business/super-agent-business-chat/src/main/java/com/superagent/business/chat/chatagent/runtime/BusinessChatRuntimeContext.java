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

/**
 * 单轮对话运行态工作台。
 *
 * <p>这是单轮对话在 JVM 内存中的工作台，只存在于一次流式回答执行期间。</p>
 *
 * <p>它承载四类运行中数据：</p>
 * <ol>
 *     <li>输入快照：{@link BusinessChatTaskInfo}，记录 conversation、exchange、model、lease 等入口事实。</li>
 *     <li>输出通道：SSE 事件通过 outputChannel 推给前端。</li>
 *     <li>回答缓冲：模型增量文本不断追加到 replyContentBuilder，最终用于归档完整回答。</li>
 *     <li>伴随信息：reasoning、source、followUp、toolTrace 和执行计划，最终一起冻结为 FinalizedTurn。</li>
 * </ol>
 *
 * <p>成功、失败和中止路径都会竞争 markFinalized；只有第一个成功的路径能冻结并归档，
 * 防止同一轮 exchange 被重复写成不同终态。</p>
 */
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

    /**
     * 冻结当前运行态为可归档快照。
     *
     * <p>冻结后得到的 {@link BusinessChatFinalizedTurn} 会被 SSE 补发、exchange 归档、debugTrace 和摘要刷新共同使用。
     * 这样同一轮回答的正文、引用、推荐追问和延迟指标来自同一时刻。</p>
     */
    public synchronized BusinessChatFinalizedTurn freezeFinalizedTurn() {
        // 冻结点之后的 SSE 补发、归档和摘要刷新都读同一份快照，避免并发追加导致各处看到的回答不一致。
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
