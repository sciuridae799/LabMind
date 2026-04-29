package com.superagent.business.chat.chatagent.model;

import com.superagent.business.chat.chatagent.agent.BusinessChatAgentType;
import com.superagent.business.chat.knowledge.model.KnowledgeRouteCandidate;
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
        BusinessChatFreshnessRequirement freshnessRequirement,
        String knowledgeRoute,
        List<KnowledgeRouteCandidate> knowledgeRouteCandidateList,
        String executionModel,
        String intentLabel,
        String intentReason,
        BusinessChatAgentType agentType,
        BusinessChatMode executionMode,
        BusinessChatClarificationPlan clarificationPlan,
        List<String> executionStepList) {
}
