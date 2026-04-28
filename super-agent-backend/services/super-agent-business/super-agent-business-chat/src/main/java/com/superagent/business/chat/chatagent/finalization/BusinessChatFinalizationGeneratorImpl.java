package com.superagent.business.chat.chatagent.finalization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.superagent.business.chat.chatagent.execution.BusinessChatDynamicModelClient;
import com.superagent.business.chat.chatagent.runtime.BusinessChatFinalizedTurn;
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

    private static final int FOLLOW_UP_COUNT = 3;

    private final BusinessChatDynamicModelClient modelClient;

    private final ObjectMapper objectMapper;

    @Override
    public BusinessChatFinalizationResult generate(BusinessChatFinalizedTurn finalizedTurn, boolean titleRequired) {
        // 收尾生成只依赖已冻结快照，避免归档过程中继续读取可变运行态。
        String userMessage = """
                titleRequired: %s

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
                finalizedTurn.taskInfo().question(),
                finalizedTurn.replyContent(),
                String.join("\n", finalizedTurn.sourceSnapshotList()),
                finalizedTurn.taskInfo().chatMode().getValue());

        String content = modelClient.call(
                finalizedTurn.taskInfo().modelConfig(),
                """
                        你是对话收尾生成器。
                        只输出一个 JSON 对象，不要输出 Markdown。
                        JSON 格式：
                        {
                          "dialogueTitle": "titleRequired 为 true 时生成 4 到 18 个中文字符的会话标题；为 false 时输出空字符串",
                          "followUpSuggestionList": ["推荐追问1", "推荐追问2", "推荐追问3"]
                        }
                        推荐追问必须基于用户问题和助手回答，面向下一轮真实对话，每条不超过 28 个中文字符。
                        """,
                userMessage);
        return parseResult(content, titleRequired);
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
        if (followUpSuggestionList == null || followUpSuggestionList.size() != FOLLOW_UP_COUNT) {
            throw new IllegalStateException("followUpSuggestionList must contain exactly 3 items.");
        }
        // 推荐追问必须固定三条且去重后仍为三条，前端可按稳定数量直接渲染。
        List<String> normalizedList = followUpSuggestionList.stream()
                .map(item -> item == null ? "" : item.strip())
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (normalizedList.size() != FOLLOW_UP_COUNT) {
            throw new IllegalStateException("followUpSuggestionList must contain exactly 3 non-empty unique items.");
        }
        return normalizedList;
    }
}
