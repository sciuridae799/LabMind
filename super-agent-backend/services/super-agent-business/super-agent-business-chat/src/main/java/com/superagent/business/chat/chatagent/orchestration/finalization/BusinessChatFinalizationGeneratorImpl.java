package com.superagent.business.chat.chatagent.orchestration.finalization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superagent.business.chat.chatagent.config.BusinessChatRecommendationProperties;
import com.superagent.business.chat.chatagent.execution.BusinessChatDynamicModelClient;
import com.superagent.business.chat.chatagent.runtime.BusinessChatFinalizedTurn;
import com.superagent.business.chat.chatagent.runtime.BusinessChatRuntimeContext;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 对话收尾内容生成器。
 *
 * <p>基于已冻结的单轮快照生成会话标题和推荐追问，并对模型返回的 JSON 结构做强校验。</p>
 */
@Service
@RequiredArgsConstructor
public class BusinessChatFinalizationGeneratorImpl implements BusinessChatFinalizationGenerator {

    private final BusinessChatDynamicModelClient modelClient;

    private final ObjectMapper objectMapper;

    private final BusinessChatRecommendationProperties recommendationProperties;

    @Override
    public BusinessChatFinalizationResult generate(
            BusinessChatRuntimeContext runtimeContext,
            BusinessChatFinalizedTurn finalizedTurn,
            boolean titleRequired) {
        if (!titleRequired && !recommendationProperties.isEnabled()) {
            return new BusinessChatFinalizationResult("", List.of());
        }
        // 收尾生成只依赖已冻结快照，避免归档过程中继续读取可变运行态。
        String userMessage = """
                titleRequired: %s
                recommendationEnabled: %s

                用户问题：
                %s

                助手回答：
                %s

                引用快照：
                %s

                执行模式：
                %s
                """.formatted(
                titleRequired,
                recommendationProperties.isEnabled(),
                finalizedTurn.taskInfo().question(),
                finalizedTurn.replyContent(),
                String.join("\n", finalizedTurn.sourceSnapshotList()),
                finalizedTurn.taskInfo().chatMode().getValue());

        String content = modelClient.call(
                runtimeContext,
                finalizedTurn.taskInfo().modelConfig(),
                buildSystemPrompt(titleRequired),
                userMessage);
        return parseResult(content, titleRequired);
    }

    private String buildSystemPrompt(boolean titleRequired) {
        String titleInstruction = titleRequired
                ? "dialogueTitle 必须生成 4 到 18 个中文字符的会话标题"
                : "dialogueTitle 必须输出空字符串";
        String followUpInstruction = recommendationProperties.isEnabled()
                ? """
                        followUpSuggestionList 必须包含 %s 条推荐追问。
                        %s
                        """.formatted(
                        recommendationProperties.getCount(),
                        requireRecommendationPrompt())
                : "followUpSuggestionList 必须输出空数组。";
        return """
                        你是对话收尾生成器。
                        只输出一个 JSON 对象，不要输出 Markdown。
                        JSON 格式：
                        {
                          "dialogueTitle": "%s",
                          "followUpSuggestionList": %s
                        }
                        %s
                        """.formatted(titleInstruction, buildFollowUpSchemaExample(), followUpInstruction);
    }

    private String buildFollowUpSchemaExample() {
        if (!recommendationProperties.isEnabled()) {
            return "[]";
        }
        return "[" + String.join(", ", java.util.stream.IntStream.rangeClosed(1, recommendationProperties.getCount())
                .mapToObj(index -> "\"推荐追问" + index + "\"")
                .toList()) + "]";
    }

    private String requireRecommendationPrompt() {
        String prompt = recommendationProperties.getPrompt() == null ? null : recommendationProperties.getPrompt().strip();
        if (!StringUtils.hasText(prompt)) {
            throw new IllegalStateException("recommendation prompt must not be blank.");
        }
        return prompt;
    }

    private BusinessChatFinalizationResult parseResult(String content, boolean titleRequired) {
        if (!StringUtils.hasText(content)) {
            throw new IllegalStateException("chat finalization payload is empty.");
        }
        try {
            BusinessChatFinalizationResult result = objectMapper.readValue(content, BusinessChatFinalizationResult.class);
            String dialogueTitle = normalizeTitle(result.dialogueTitle(), titleRequired);
            List<String> followUpSuggestionList = normalizeFollowUpSuggestionList(result.followUpSuggestionList());
            return new BusinessChatFinalizationResult(dialogueTitle, followUpSuggestionList);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to parse chat finalization payload.", exception);
        }
    }

    private String normalizeTitle(String dialogueTitle, boolean titleRequired) {
        String normalizedTitle = dialogueTitle == null ? "" : dialogueTitle.strip();
        if (titleRequired && !StringUtils.hasText(normalizedTitle)) {
            throw new IllegalStateException("dialogueTitle is required.");
        }
        if (normalizedTitle.contains("\n") || normalizedTitle.contains("\r")) {
            throw new IllegalStateException("dialogueTitle must be a single line.");
        }
        return normalizedTitle;
    }

    private List<String> normalizeFollowUpSuggestionList(List<String> followUpSuggestionList) {
        int requiredCount = recommendationProperties.isEnabled() ? recommendationProperties.getCount() : 0;
        if (followUpSuggestionList == null || followUpSuggestionList.size() != requiredCount) {
            throw new IllegalStateException("followUpSuggestionList must contain exactly " + requiredCount + " items.");
        }
        // 推荐追问数量来自配置且去重后仍要满足要求，前端可按稳定数量直接渲染。
        List<String> normalizedList = followUpSuggestionList.stream()
                .map(item -> item == null ? "" : item.strip())
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (normalizedList.size() != requiredCount) {
            throw new IllegalStateException(
                    "followUpSuggestionList must contain exactly " + requiredCount + " non-empty unique items.");
        }
        return normalizedList;
    }
}
