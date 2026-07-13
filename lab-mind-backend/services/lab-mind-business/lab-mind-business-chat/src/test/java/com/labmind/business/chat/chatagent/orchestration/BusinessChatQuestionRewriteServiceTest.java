package com.labmind.business.chat.chatagent.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.labmind.business.chat.chatagent.config.BusinessChatRewriteProperties;
import com.labmind.business.chat.chatagent.execution.BusinessChatDynamicModelClient;
import com.labmind.business.chat.chatagent.execution.model.BusinessChatModelApiConfigSnapshot;
import com.labmind.business.chat.chatagent.execution.model.BusinessChatModelProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BusinessChatQuestionRewriteServiceTest {

    @Mock
    private BusinessChatDynamicModelClient modelClient;

    private BusinessChatQuestionRewriteService rewriteService;

    private BusinessChatModelApiConfigSnapshot modelConfig;

    @BeforeEach
    void setUp() {
        rewriteService = new BusinessChatQuestionRewriteService(
                modelClient,
                new ObjectMapper(),
                new BusinessChatRewriteProperties());
        modelConfig = new BusinessChatModelApiConfigSnapshot(
                3001L,
                BusinessChatModelProvider.DASHSCOPE,
                "DASHSCOPE",
                "https://dashscope.aliyuncs.com/compatible-mode",
                "qwen-plus",
                "api-key", java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, 1000, "CNY");
    }

    @Test
    void shouldReturnOriginalQuestionWhenHistoryContextIsEmpty() {
        String rewrittenQuestion = rewriteService.rewrite(null, "这个怎么配置？", null, modelConfig);

        assertThat(rewrittenQuestion).isEqualTo("这个怎么配置？");
        verify(modelClient, never()).call(any(), any(), any(), any());
    }

    @Test
    void shouldReturnOriginalQuestionWhenQuestionHasNoContextDependency() {
        String rewrittenQuestion = rewriteService.rewrite(
                null,
                "订单审核链路有哪些风险？",
                "长期摘要：用户在讨论订单审核链路。",
                modelConfig);

        assertThat(rewrittenQuestion).isEqualTo("订单审核链路有哪些风险？");
        verify(modelClient, never()).call(any(), any(), any(), any());
    }

    @Test
    void shouldRewriteContextDependentQuestionToStandaloneQuestion() {
        when(modelClient.call(any(), any(), any(), contains("当前问题：\n这个有哪些风险？")))
                .thenReturn("{\"rewrite\":\"订单审核链路有哪些风险？\"}");

        String rewrittenQuestion = rewriteService.rewrite(
                null,
                "这个有哪些风险？",
                "长期摘要：用户之前在讨论订单审核链路。",
                modelConfig);

        assertThat(rewrittenQuestion).isEqualTo("订单审核链路有哪些风险？");
        verify(modelClient).call(any(), any(), contains("你是企业对话系统的问题改写器"), any());
    }

    @Test
    void shouldRewriteNextQuestionWhenPreviousAnswerContainsExamQuestion() {
        when(modelClient.call(any(), any(), any(), contains("当前问题：\n下一题呢")))
                .thenReturn("{\"rewrite\":\"模拟试卷（十一）选择题第11题是什么？\"}");

        String rewrittenQuestion = rewriteService.rewrite(
                null,
                "下一题呢",
                """
                        最近对话：
                        时间：2026-06-03T17:08
                        用户：模拟试卷第10题是什么
                        助手：根据检索证据，模拟试卷（十一）选择题第10题原文为：
                        人类适应不包括的层次为（ ）。
                        A. 知识技术层次
                        B. 生理层次
                        C. 心理层次
                        D. 社会文化层次
                        E. 情感精神层次
                        """,
                modelConfig);

        assertThat(rewrittenQuestion).isEqualTo("模拟试卷（十一）选择题第11题是什么？");
        verify(modelClient).call(any(), any(), contains("你是企业对话系统的问题改写器"), any());
    }

    @Test
    void shouldRejectInvalidJsonResponse() {
        when(modelClient.call(any(), any(), any(), any()))
                .thenReturn("订单审核链路有哪些风险？", "仍然不是 JSON");

        assertThatThrownBy(() -> rewriteService.rewrite(
                null,
                "这个有哪些风险？",
                "长期摘要：用户之前在讨论订单审核链路。",
                modelConfig))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("question rewrite correction failed after initial failure");
        verify(modelClient, times(2)).call(any(), any(), any(), any());
    }

    @Test
    void shouldRejectEmptyRewrite() {
        when(modelClient.call(any(), any(), any(), any()))
                .thenReturn("{\"rewrite\":\"\"}", "{\"rewrite\":\"\"}");

        assertThatThrownBy(() -> rewriteService.rewrite(
                null,
                "这个有哪些风险？",
                "长期摘要：用户之前在讨论订单审核链路。",
                modelConfig))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("question rewrite correction failed after initial failure");
        verify(modelClient, times(2)).call(any(), any(), any(), any());
    }

    @Test
    void shouldCorrectInvalidJsonResponseOnce() {
        when(modelClient.call(any(), any(), any(), any()))
                .thenReturn(
                        "订单审核链路有哪些风险？",
                        "{\"rewrite\":\"订单审核链路有哪些风险？\"}");

        String rewrittenQuestion = rewriteService.rewrite(
                null,
                "这个有哪些风险？",
                "长期摘要：用户之前在讨论订单审核链路。",
                modelConfig);

        assertThat(rewrittenQuestion).isEqualTo("订单审核链路有哪些风险？");
        verify(modelClient, times(2)).call(any(), any(), any(), any());
        verify(modelClient).call(any(), any(), contains("问题改写结果纠错器"), contains("上一次失败原因"));
    }
}
