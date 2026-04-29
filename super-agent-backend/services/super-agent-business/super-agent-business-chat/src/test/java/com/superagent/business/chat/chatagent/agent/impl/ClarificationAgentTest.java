package com.superagent.business.chat.chatagent.agent.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.superagent.business.chat.chatagent.agent.BusinessChatAgentType;
import com.superagent.business.chat.chatagent.model.BusinessChatClarificationPlan;
import com.superagent.business.chat.chatagent.model.BusinessChatExecutionPlan;
import com.superagent.business.chat.chatagent.model.BusinessChatFreshnessRequirement;
import com.superagent.business.chat.chatagent.model.BusinessChatMode;
import com.superagent.business.chat.chatagent.model.BusinessChatModelApiConfigSnapshot;
import com.superagent.business.chat.chatagent.model.BusinessChatModelProvider;
import com.superagent.business.chat.chatagent.model.BusinessChatTaskInfo;
import com.superagent.business.chat.chatagent.runtime.BusinessChatRuntimeContext;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Sinks;

class ClarificationAgentTest {

    private final ClarificationAgent agent = new ClarificationAgent();

    @Test
    void shouldReturnClarificationReplyWithoutModelCall() {
        BusinessChatExecutionPlan executionPlan = buildExecutionPlan(new BusinessChatClarificationPlan(
                true,
                "知识路由没有召回候选文档",
                "当前没有匹配到稳定的候选文档。",
                List.of()));

        List<String> result = agent.execute(buildRuntimeContext(), executionPlan).collectList().block();

        assertThat(agent.agentType()).isEqualTo(BusinessChatAgentType.CLARIFICATION);
        assertThat(result).containsExactly("当前没有匹配到稳定的候选文档。");
    }

    @Test
    void shouldRejectMissingClarificationPlan() {
        BusinessChatExecutionPlan executionPlan = buildExecutionPlan(BusinessChatClarificationPlan.notRequired());

        assertThatThrownBy(() -> agent.execute(buildRuntimeContext(), executionPlan).collectList().block())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("clarification plan is required");
    }

    private BusinessChatExecutionPlan buildExecutionPlan(BusinessChatClarificationPlan clarificationPlan) {
        return new BusinessChatExecutionPlan(
                "问题",
                "问题",
                null,
                null,
                null,
                0,
                null,
                new BusinessChatFreshnessRequirement(false, "无需实时信息", List.of(), "NOT_REQUIRED"),
                "KNOWLEDGE_BASE|CLARIFICATION_REQUIRED",
                List.of(),
                "qwen-plus",
                "knowledge_route_clarification",
                clarificationPlan.reason(),
                BusinessChatAgentType.CLARIFICATION,
                BusinessChatMode.KNOWLEDGE_BASE,
                clarificationPlan,
                List.of("歧义澄清：" + clarificationPlan.reason()));
    }

    private BusinessChatRuntimeContext buildRuntimeContext() {
        BusinessChatTaskInfo taskInfo = new BusinessChatTaskInfo(
                1001L,
                2001L,
                "问题",
                "conversation-1",
                BusinessChatMode.KNOWLEDGE_BASE,
                new BusinessChatModelApiConfigSnapshot(
                        3001L,
                        BusinessChatModelProvider.DASHSCOPE,
                        "DASHSCOPE",
                        "https://dashscope.aliyuncs.com/compatible-mode",
                        "qwen-plus",
                        "api-key"),
                null,
                null,
                "trace-1",
                "chat:conversation:running:conversation-1",
                "owner-1",
                Duration.ofSeconds(30),
                System.currentTimeMillis());
        return new BusinessChatRuntimeContext(taskInfo, Sinks.many().unicast().onBackpressureBuffer());
    }
}
