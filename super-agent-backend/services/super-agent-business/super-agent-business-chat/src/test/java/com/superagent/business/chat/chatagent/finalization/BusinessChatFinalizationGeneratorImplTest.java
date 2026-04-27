package com.superagent.business.chat.chatagent.finalization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.superagent.business.chat.chatagent.agent.BusinessChatAgentType;
import com.superagent.business.chat.chatagent.execution.BusinessChatDynamicModelClient;
import com.superagent.business.chat.chatagent.model.BusinessChatExecutionPlan;
import com.superagent.business.chat.chatagent.model.BusinessChatFreshnessRequirement;
import com.superagent.business.chat.chatagent.model.BusinessChatIntentAnalysis;
import com.superagent.business.chat.chatagent.model.BusinessChatMode;
import com.superagent.business.chat.chatagent.model.BusinessChatModelApiConfigSnapshot;
import com.superagent.business.chat.chatagent.model.BusinessChatModelProvider;
import com.superagent.business.chat.chatagent.model.BusinessChatTaskInfo;
import com.superagent.business.chat.chatagent.runtime.BusinessChatFinalizedTurn;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BusinessChatFinalizationGeneratorImplTest {

    @Mock
    private BusinessChatDynamicModelClient modelClient;

    private BusinessChatFinalizationGeneratorImpl generator;

    @BeforeEach
    void setUp() {
        generator = new BusinessChatFinalizationGeneratorImpl(modelClient, new ObjectMapper());
    }

    @Test
    void shouldUseFinalizedTurnModelConfigForFinalization() {
        BusinessChatModelApiConfigSnapshot selectedModelConfig = new BusinessChatModelApiConfigSnapshot(
                3002L,
                BusinessChatModelProvider.DEEPSEEK,
                "DeepSeek V4",
                "https://api.deepseek.com",
                "deepseek-v4-pro",
                "api-key");
        when(modelClient.call(eq(selectedModelConfig), anyString(), anyString()))
                .thenReturn("""
                        {
                          "dialogueTitle": "模型切换验证",
                          "followUpSuggestionList": ["继续验证链路？", "查看执行模型？", "检查归档内容？"]
                        }
                        """);

        BusinessChatFinalizationResult result = generator.generate(createFinalizedTurn(selectedModelConfig), true);

        assertThat(result.dialogueTitle()).isEqualTo("模型切换验证");
        assertThat(result.followUpSuggestionList()).containsExactly("继续验证链路？", "查看执行模型？", "检查归档内容？");
        ArgumentCaptor<BusinessChatModelApiConfigSnapshot> modelConfigCaptor =
                ArgumentCaptor.forClass(BusinessChatModelApiConfigSnapshot.class);
        verify(modelClient).call(modelConfigCaptor.capture(), anyString(), anyString());
        assertThat(modelConfigCaptor.getValue()).isEqualTo(selectedModelConfig);
    }

    private BusinessChatFinalizedTurn createFinalizedTurn(BusinessChatModelApiConfigSnapshot modelConfig) {
        BusinessChatTaskInfo taskInfo = new BusinessChatTaskInfo(
                1001L,
                2001L,
                "同一会话切换模型是否生效？",
                "conversation-1",
                BusinessChatMode.OPEN_ENDED,
                modelConfig,
                null,
                null,
                "trace-1",
                "chat:conversation:running:conversation-1",
                "owner-1",
                Duration.ofSeconds(30),
                System.currentTimeMillis());
        BusinessChatExecutionPlan executionPlan = new BusinessChatExecutionPlan(
                taskInfo.question(),
                taskInfo.question(),
                null,
                null,
                0,
                null,
                new BusinessChatFreshnessRequirement(false, "无需实时信息", List.of(), "NOT_REQUIRED"),
                "NOT_REQUIRED",
                List.of(),
                modelConfig.modelName(),
                "open_ended_question_answer",
                "根据本轮输入生成执行计划。",
                BusinessChatAgentType.THINK_ACT,
                BusinessChatMode.OPEN_ENDED,
                List.of("执行模型：" + modelConfig.modelName()));
        return new BusinessChatFinalizedTurn(
                taskInfo,
                "已使用本轮选择的模型生成回答。",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                new BusinessChatIntentAnalysis(
                        executionPlan.intentLabel(),
                        executionPlan.intentReason(),
                        executionPlan.executionMode()),
                executionPlan,
                12L,
                120L);
    }
}
