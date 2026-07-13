package com.labmind.business.chat.chatagent.execution.impl;

import com.labmind.business.chat.chatagent.orchestration.model.BusinessChatExecutionPlan;
import com.labmind.business.chat.knowledge.route.model.KnowledgeRouteCandidate;
import java.util.List;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
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
        return streamResponse(executionPlan).map(this::extractText);
    }

    BusinessChatModelPrompt buildPrompt(BusinessChatExecutionPlan executionPlan) {
        // 执行计划里的路由和上下文只作为模型输入边界，不向用户暴露内部编排细节。
        String systemPrompt = """
                你是超级智能的对话助手。
                当前执行模式：%s。
                知识路由：%s。
                执行模型：%s。
                时效性要求：%s。
                历史上下文只用于理解用户延续话题；如果历史上下文中的模型身份、模型名称或执行模型与当前执行模型冲突，必须以当前执行模型为准。
                如果时效性要求为需要实时信息且实时能力不可用，请明确说明无法验证实时信息，不要编造今天、当前、最新的外部事实。
                知识库模式下，知识路由候选只表示检索范围，不等于正文证据；只能基于“检索证据上下文”给出文档事实结论。
                当前文档问答模式下，只能围绕“当前文档画像上下文”和“检索证据上下文”回答；如果检索证据不足以支持结论，请说明当前文档证据不足。
                如果使用检索证据回答，必须在对应事实后标注证据编号，例如 [1]、[2]；不得引用未出现在“检索证据上下文”中的编号。
                如果“检索证据上下文”为“无”，不得生成文档事实结论。
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

                当前文档画像上下文：
                %s

                检索证据上下文：
                %s

                当前问题：
                %s
                """.formatted(
                executionPlan.answerHistoryContextText() == null ? "无" : executionPlan.answerHistoryContextText(),
                buildKnowledgeRouteCandidateText(executionPlan.knowledgeRouteCandidateList()),
                executionPlan.selectedDocumentContextText() == null ? "无" : executionPlan.selectedDocumentContextText(),
                executionPlan.retrievalEvidenceContextText() == null ? "无" : executionPlan.retrievalEvidenceContextText(),
                executionPlan.rewrittenQuestion());
        return new BusinessChatModelPrompt(systemPrompt, userMessage);
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

    public Flux<ChatResponse> streamResponse(BusinessChatExecutionPlan executionPlan) {
        return streamResponse(buildPrompt(executionPlan));
    }

    public Flux<ChatResponse> streamResponse(BusinessChatModelPrompt prompt) {
        return chatClient.prompt()
                .system(prompt.systemPrompt())
                .user(prompt.userPrompt())
                .stream()
                .chatResponse();
    }

    public String call(String systemPrompt, String userMessage) {
        return chatClient.prompt()
                .system(systemPrompt)
                .user(userMessage)
                .call()
                .content();
    }

    public ChatResponse callResponse(String systemPrompt, String userMessage) {
        return callResponse(new BusinessChatModelPrompt(systemPrompt, userMessage));
    }

    public ChatResponse callResponse(BusinessChatModelPrompt prompt) {
        return chatClient.prompt()
                .system(prompt.systemPrompt())
                .user(prompt.userPrompt())
                .call()
                .chatResponse();
    }

    String extractText(ChatResponse response) {
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            return "";
        }
        return response.getResult().getOutput().getText();
    }
}
