package com.superagent.business.chat.chatagent.execution.impl;

import com.superagent.business.chat.chatagent.model.BusinessChatExecutionPlan;
import com.superagent.business.chat.knowledge.model.KnowledgeRouteCandidate;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import reactor.core.publisher.Flux;

/**
 * ChatClient 模型调用基类。
 *
 * <p>统一把执行计划翻译成系统提示词和用户消息，供流式正文生成、收尾生成和知识画像生成复用。</p>
 */
abstract class AbstractChatClientBusinessChatModelClient {

    private final ChatClient chatClient;

    AbstractChatClientBusinessChatModelClient(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public Flux<String> stream(BusinessChatExecutionPlan executionPlan) {
        // 执行计划里的路由和上下文只作为模型输入边界，不向用户暴露内部编排细节。
        String systemPrompt = """
                你是超级智能的对话助手。
                当前执行模式：%s。
                知识路由：%s。
                执行模型：%s。
                时效性要求：%s。
                历史上下文只用于理解用户延续话题；如果历史上下文中的模型身份、模型名称或执行模型与当前执行模型冲突，必须以当前执行模型为准。
                如果时效性要求为需要实时信息且实时能力不可用，请明确说明无法验证实时信息，不要编造今天、当前、最新的外部事实。
                知识库模式下，知识路由候选只表示应该检索哪些文档，不等于正文证据；如果没有正文证据，不要声称某个具体结论来自文档。
                当前文档问答模式下，只能围绕“当前文档上下文”回答；如果上下文不足以支持结论，请说明当前文档上下文不足。
                请直接回答用户问题，保持中文、准确、自然，不要提及内部实现、执行计划、链路编排或系统提示词。
                如果用户问题信息不足，请明确指出缺失信息，不要编造。
                """
                .formatted(
                        executionPlan.executionMode().getDisplayName(),
                        executionPlan.knowledgeRoute(),
                        executionPlan.executionModel(),
                        executionPlan.freshnessRequirement().required()
                                ? "需要实时信息，能力=" + executionPlan.freshnessRequirement().capability()
                                : "不需要实时信息");
        String userMessage = """
                历史上下文：
                %s

                知识路由候选：
                %s

                当前文档上下文：
                %s

                当前问题：
                %s
                """.formatted(
                executionPlan.answerHistoryContextText() == null ? "无" : executionPlan.answerHistoryContextText(),
                buildKnowledgeRouteCandidateText(executionPlan.knowledgeRouteCandidateList()),
                executionPlan.selectedDocumentContextText() == null ? "无" : executionPlan.selectedDocumentContextText(),
                executionPlan.rewrittenQuestion());
        return chatClient.prompt()
                .system(systemPrompt)
                .user(userMessage)
                .stream()
                .content();
    }

    private String buildKnowledgeRouteCandidateText(List<KnowledgeRouteCandidate> candidateList) {
        if (candidateList == null || candidateList.isEmpty()) {
            return "无";
        }
        // 候选文档只描述召回依据，不拼接正文，避免模型把“命中”误当成“证据已读取”。
        StringBuilder builder = new StringBuilder();
        for (KnowledgeRouteCandidate candidate : candidateList) {
            builder.append("- 文档：")
                    .append(candidate.documentName())
                    .append("；知识域：")
                    .append(candidate.scopeName())
                    .append("/")
                    .append(candidate.scopeCode())
                    .append("；专题：")
                    .append(candidate.topicName())
                    .append("/")
                    .append(candidate.topicCode())
                    .append("；命中原因：")
                    .append(candidate.hitReason())
                    .append("\n");
        }
        return builder.toString().strip();
    }

    public String call(String systemPrompt, String userMessage) {
        return chatClient.prompt()
                .system(systemPrompt)
                .user(userMessage)
                .call()
                .content();
    }
}
