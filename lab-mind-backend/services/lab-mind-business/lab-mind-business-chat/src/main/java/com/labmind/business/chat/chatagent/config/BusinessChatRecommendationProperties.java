package com.labmind.business.chat.chatagent.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "lab-mind.chat.recommendation")
public class BusinessChatRecommendationProperties {

    private boolean enabled = true;

    @Min(1)
    private int count = 3;

    @Min(0)
    private int historyPreviewTurns = 4;

    private String prompt = """
            你是推荐问题生成助手。
            请根据用户问题和助手回答，生成 3 个适合继续追问的中文问题。

            要求：
            1. 返回 JSON 对象中的 followUpSuggestionList 字段。
            2. 每个问题都要自然、具体、可继续追问。
            3. 不要和当前问题重复。
            4. 每个问题尽量控制在 28 个中文字符以内。
            """;
}
