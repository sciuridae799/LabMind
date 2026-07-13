package com.labmind.business.chat.chatagent.orchestration.model;

import com.labmind.business.chat.knowledge.route.model.KnowledgeRouteCandidate;
import com.labmind.business.chat.knowledge.retrieval.KnowledgeRetrievalParentEvidence;
import java.util.List;

/**
 * 编排器生成的本轮执行计划。
 *
 * <p>主服务用它选择执行器、推送执行补充信息；执行器用它组装模型输入。</p>
 */
public record BusinessChatExecutionPlan(
        String originalQuestion,
        String rewrittenQuestion,
        String rewriteHistoryContextText,
        String answerHistoryContextText,
        String memorySummary,
        int recentExchangeCount,
        String selectedDocumentContextText,
        String retrievalEvidenceContextText,
        List<KnowledgeRetrievalParentEvidence> retrievalEvidenceList,
        BusinessChatFreshnessRequirement freshnessRequirement,
        String knowledgeRoute,
        List<KnowledgeRouteCandidate> knowledgeRouteCandidateList,
        String executionModel,
        String intentLabel,
        String intentReason,
        List<BusinessChatAgentStep> agentStepList,
        BusinessChatMode executionMode,
        BusinessChatClarificationPlan clarificationPlan,
        boolean shortCircuit,
        String shortCircuitReply,
        List<String> executionStepList) {

    public BusinessChatExecutionPlan {
        agentStepList = List.copyOf(agentStepList == null ? List.of() : agentStepList);
    }

    public BusinessChatAgentStep answerAgentStep() {
        return agentStepList.stream()
                .filter(BusinessChatAgentStep::answerProducer)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("answer producer agent step is required."));
    }
}
