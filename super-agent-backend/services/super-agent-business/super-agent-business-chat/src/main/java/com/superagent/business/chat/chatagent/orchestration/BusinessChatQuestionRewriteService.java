package com.superagent.business.chat.chatagent.orchestration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superagent.business.chat.chatagent.config.BusinessChatRewriteProperties;
import com.superagent.business.chat.chatagent.execution.BusinessChatDynamicModelClient;
import com.superagent.business.chat.chatagent.model.BusinessChatModelApiConfigSnapshot;
import com.superagent.business.chat.chatagent.runtime.BusinessChatRuntimeContext;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 对话问题改写服务。
 *
 * <p>只在当前问题依赖历史上下文时，把承接式问题改写成可独立路由和回答的问题。</p>
 */
@Service
@RequiredArgsConstructor
public class BusinessChatQuestionRewriteService {

    private static final List<String> CONTEXT_DEPENDENCY_SIGNAL_LIST = List.of(
            "它",
            "它们",
            "他们",
            "这个",
            "这些",
            "这里",
            "这块",
            "上述",
            "上面",
            "刚才",
            "前面",
            "前者",
            "后者",
            "该方案",
            "这种方式",
            "那",
            "然后呢",
            "继续",
            "还有呢");

    private final BusinessChatDynamicModelClient modelClient;

    private final ObjectMapper objectMapper;

    private final BusinessChatRewriteProperties rewriteProperties;

    public String rewrite(
            BusinessChatRuntimeContext runtimeContext,
            String originalQuestion,
            String historyContextText,
            BusinessChatModelApiConfigSnapshot modelConfig) {
        String normalizedQuestion = normalizeQuestion(originalQuestion);
        if (!StringUtils.hasText(historyContextText) || !hasContextDependency(normalizedQuestion)) {
            return normalizedQuestion;
        }
        String modelResponse = callRewriteModel(runtimeContext, normalizedQuestion, historyContextText, modelConfig);
        try {
            return parseRewrite(modelResponse);
        } catch (IllegalStateException initialFailure) {
            if (!rewriteProperties.isCorrectionRetryEnabled()) {
                throw initialFailure;
            }
            String correctionResponse = callCorrectionModel(
                    runtimeContext,
                    normalizedQuestion,
                    historyContextText,
                    modelConfig,
                    modelResponse,
                    initialFailure.getMessage());
            try {
                return parseRewrite(correctionResponse);
            } catch (IllegalStateException correctionFailure) {
                throw new IllegalStateException(
                        "question rewrite correction failed after initial failure: " + initialFailure.getMessage(),
                        correctionFailure);
            }
        }
    }

    private String callRewriteModel(
            BusinessChatRuntimeContext runtimeContext,
            String normalizedQuestion,
            String historyContextText,
            BusinessChatModelApiConfigSnapshot modelConfig) {
        return modelClient.call(
                runtimeContext,
                modelConfig,
                """
                        你是企业对话系统的问题改写器。
                        你的任务：结合历史上下文，把当前问题改写成一个可以脱离上下文独立理解的问题。

                        只允许做：
                        1. 补全当前问题中的指代对象、省略对象和承接对象。
                        2. 保留当前问题原有的动作、范围、时间、环境、角色、限制条件。
                        3. 当前问题已经完整时，原样返回。

                        禁止做：
                        1. 不得回答问题。
                        2. 不得扩展问题范围。
                        3. 不得添加历史上下文和当前问题都没有明确出现的信息。
                        4. 不得规划回答结构、章节、方面、维度。
                        5. 不得把一个问题拆成多个问题。

                        只输出一个合法 JSON 对象，不要输出 Markdown。
                        JSON 格式：
                        {
                          "rewrite": "改写后的独立问题"
                        }
                        """,
                """
                        历史上下文：
                        %s

                        当前问题：
                        %s
                        """.formatted(historyContextText.strip(), normalizedQuestion));
    }

    private String callCorrectionModel(
            BusinessChatRuntimeContext runtimeContext,
            String normalizedQuestion,
            String historyContextText,
            BusinessChatModelApiConfigSnapshot modelConfig,
            String invalidModelResponse,
            String failureReason) {
        return modelClient.call(
                runtimeContext,
                modelConfig,
                """
                        你是企业对话系统的问题改写结果纠错器。
                        你的任务：把上一次问题改写模型的输出修正为合法 JSON。

                        只允许做：
                        1. 从上一次输出中提取或修正改写问题。
                        2. 必须继续遵守原始问题改写约束：只补全指代对象、省略对象和承接对象。
                        3. 保留当前问题原有动作、范围、时间、环境、角色、限制条件。

                        禁止做：
                        1. 不得回答问题。
                        2. 不得扩展问题范围。
                        3. 不得添加历史上下文和当前问题都没有明确出现的信息。
                        4. 不得把一个问题拆成多个问题。
                        5. 不得解释错误原因。

                        只输出一个合法 JSON 对象，不要输出 Markdown。
                        JSON 格式：
                        {
                          "rewrite": "改写后的独立问题"
                        }
                        """,
                """
                        历史上下文：
                        %s

                        当前问题：
                        %s

                        上一次模型输出：
                        %s

                        上一次失败原因：
                        %s
                        """.formatted(
                        historyContextText.strip(),
                        normalizedQuestion,
                        invalidModelResponse == null ? "" : invalidModelResponse.strip(),
                        failureReason));
    }

    private String normalizeQuestion(String question) {
        String normalizedQuestion = question == null ? "" : question.strip();
        if (!StringUtils.hasText(normalizedQuestion)) {
            throw new IllegalStateException("chat question is empty.");
        }
        return normalizedQuestion;
    }

    private boolean hasContextDependency(String question) {
        return CONTEXT_DEPENDENCY_SIGNAL_LIST.stream().anyMatch(question::contains);
    }

    private String parseRewrite(String modelResponse) {
        if (!StringUtils.hasText(modelResponse)) {
            throw new IllegalStateException("question rewrite payload is empty.");
        }
        try {
            JsonNode root = objectMapper.readTree(modelResponse.strip());
            JsonNode rewriteNode = root.get("rewrite");
            String rewrite = rewriteNode == null || rewriteNode.isNull() ? "" : rewriteNode.asText("").strip();
            if (!StringUtils.hasText(rewrite)) {
                throw new IllegalStateException("question rewrite payload must contain non-empty rewrite.");
            }
            if (rewrite.contains("\n") || rewrite.contains("\r")) {
                throw new IllegalStateException("question rewrite must be a single line.");
            }
            return rewrite;
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("question rewrite model response must be a JSON object.", exception);
        }
    }
}
