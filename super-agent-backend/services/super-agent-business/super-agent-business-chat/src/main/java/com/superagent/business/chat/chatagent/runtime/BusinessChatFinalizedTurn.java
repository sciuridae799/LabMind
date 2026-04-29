package com.superagent.business.chat.chatagent.runtime;

import com.superagent.business.chat.chatagent.model.BusinessChatExecutionPlan;
import com.superagent.business.chat.chatagent.model.BusinessChatIntentAnalysis;
import com.superagent.business.chat.chatagent.model.BusinessChatTaskInfo;
import java.util.List;

/**
 * 本轮收尾冻结快照。
 *
 * <p>SSE 补发、归档和摘要刷新统一消费这份不可变数据。</p>
 */
public record BusinessChatFinalizedTurn(
        BusinessChatTaskInfo taskInfo,
        String replyContent,
        List<String> reasoningNoteList,
        List<String> sourceSnapshotList,
        List<String> followUpSuggestionList,
        List<String> toolTraceList,
        BusinessChatIntentAnalysis intentAnalysis,
        BusinessChatExecutionPlan executionPlan,
        long modelCallCount,
        long firstTokenLatencyMs,
        long totalLatencyMs) {

    public BusinessChatFinalizedTurn withFollowUpSuggestionList(List<String> finalizedFollowUpSuggestionList) {
        return new BusinessChatFinalizedTurn(
                taskInfo,
                replyContent,
                reasoningNoteList,
                sourceSnapshotList,
                List.copyOf(finalizedFollowUpSuggestionList),
                toolTraceList,
                intentAnalysis,
                executionPlan,
                modelCallCount,
                firstTokenLatencyMs,
                totalLatencyMs);
    }
}
