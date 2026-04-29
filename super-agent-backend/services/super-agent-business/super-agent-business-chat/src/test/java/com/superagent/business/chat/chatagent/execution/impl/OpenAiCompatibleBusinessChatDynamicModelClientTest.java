package com.superagent.business.chat.chatagent.execution.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.superagent.business.chat.chatagent.config.BusinessChatRuntimeProperties;
import com.superagent.business.chat.chatagent.agent.BusinessChatAgentType;
import com.superagent.business.chat.chatagent.model.BusinessChatClarificationPlan;
import com.superagent.business.chat.chatagent.model.BusinessChatModelApiConfigSnapshot;
import com.superagent.business.chat.chatagent.model.BusinessChatExecutionPlan;
import com.superagent.business.chat.chatagent.model.BusinessChatFreshnessRequirement;
import com.superagent.business.chat.chatagent.model.BusinessChatMode;
import com.superagent.business.chat.chatagent.model.BusinessChatModelProvider;
import com.superagent.business.chat.chatagent.model.BusinessChatTaskInfo;
import com.superagent.business.chat.chatagent.runtime.BusinessChatRuntimeContext;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.retry.support.RetryTemplate;
import reactor.core.publisher.Sinks;

class OpenAiCompatibleBusinessChatDynamicModelClientTest {

    private OpenAiCompatibleBusinessChatDynamicModelClient modelClient;

    @BeforeEach
    void setUp() {
        modelClient = new OpenAiCompatibleBusinessChatDynamicModelClient(
                ToolCallingManager.builder().build(),
                RetryTemplate.defaultInstance(),
                new BusinessChatRuntimeProperties(),
                mock(RedissonClient.class));
    }

    @Test
    void shouldDisableThinkingForNonStreamingCalls() {
        var options = modelClient.buildChatOptions(
                new BusinessChatModelApiConfigSnapshot(
                        3001L,
                        BusinessChatModelProvider.DASHSCOPE,
                        "Qwen",
                        "https://dashscope.aliyuncs.com/compatible-mode",
                        "qwen3-32b",
                        "api-key"),
                false);

        assertThat(options.getModel()).isEqualTo("qwen3-32b");
        assertThat(options.getExtraBody()).containsEntry("enable_thinking", false);
    }

    @Test
    void shouldNotSendThinkingParameterForStreamingCalls() {
        var options = modelClient.buildChatOptions(
                new BusinessChatModelApiConfigSnapshot(
                        3001L,
                        BusinessChatModelProvider.DASHSCOPE,
                        "Qwen",
                        "https://dashscope.aliyuncs.com/compatible-mode",
                        "qwen3-32b",
                        "api-key"),
                true);

        assertThat(options.getModel()).isEqualTo("qwen3-32b");
        assertThat(options.getExtraBody()).isNull();
    }

    @Test
    void shouldSendSelectedModelInNonStreamingHttpRequestBody() throws IOException {
        CapturingOpenAiServer server = CapturingOpenAiServer.start(false);
        try {
            modelClient.call(
                    buildModelConfig(server.baseUrl(), BusinessChatModelProvider.DASHSCOPE, "qwen3-32b"),
                    "system",
                    "user");

            assertThat(server.requestBodies()).singleElement()
                    .satisfies(body -> assertThat(body)
                            .contains("\"model\":\"qwen3-32b\"")
                            .contains("\"enable_thinking\":false"));
        } finally {
            server.stop();
        }
    }

    @Test
    void shouldSendSelectedModelInStreamingHttpRequestBody() throws IOException {
        CapturingOpenAiServer server = CapturingOpenAiServer.start(true);
        try {
            List<String> content = modelClient.stream(
                            buildModelConfig(server.baseUrl(), BusinessChatModelProvider.DASHSCOPE, "qwen-plus"),
                            buildExecutionPlan("qwen-plus"))
                    .collectList()
                    .block(Duration.ofSeconds(5));

            assertThat(content).containsExactly("ok");
            assertThat(server.requestBodies()).singleElement()
                    .satisfies(body -> assertThat(body)
                            .contains("\"model\":\"qwen-plus\"")
                            .contains("\"stream\":true")
                            .doesNotContain("enable_thinking"));
        } finally {
            server.stop();
        }
    }

    @Test
    void shouldRejectWhenRunModelCallLimitExceeded() {
        BusinessChatRuntimeProperties properties = new BusinessChatRuntimeProperties();
        properties.setMaxModelCallsPerRun(1);
        OpenAiCompatibleBusinessChatDynamicModelClient limitedClient = new OpenAiCompatibleBusinessChatDynamicModelClient(
                ToolCallingManager.builder().build(),
                RetryTemplate.defaultInstance(),
                properties,
                mock(RedissonClient.class));
        BusinessChatRuntimeContext runtimeContext = buildRuntimeContext();
        runtimeContext.incrementModelCallCount();

        assertThatThrownBy(() -> limitedClient.call(runtimeContext, runtimeContext.getTaskInfo().modelConfig(), "system", "user"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("model call limit exceeded for current run");
    }

    @Test
    void shouldRejectWhenThreadModelCallLimitExceeded() {
        BusinessChatRuntimeProperties properties = new BusinessChatRuntimeProperties();
        properties.setMaxModelCallsPerThread(1);
        RedissonClient redissonClient = mock(RedissonClient.class);
        RAtomicLong atomicLong = mock(RAtomicLong.class);
        when(redissonClient.getAtomicLong("super-agent:chat:model-calls:thread:conversation-1"))
                .thenReturn(atomicLong);
        when(atomicLong.incrementAndGet()).thenReturn(2L);
        OpenAiCompatibleBusinessChatDynamicModelClient limitedClient = new OpenAiCompatibleBusinessChatDynamicModelClient(
                ToolCallingManager.builder().build(),
                RetryTemplate.defaultInstance(),
                properties,
                redissonClient);
        BusinessChatRuntimeContext runtimeContext = buildRuntimeContext();

        assertThatThrownBy(() -> limitedClient.call(runtimeContext, runtimeContext.getTaskInfo().modelConfig(), "system", "user"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("model call limit exceeded for conversation");
    }

    private BusinessChatModelApiConfigSnapshot buildModelConfig(
            String baseUrl,
            BusinessChatModelProvider provider,
            String modelName) {
        return new BusinessChatModelApiConfigSnapshot(
                3002L,
                provider,
                provider.getDisplayName(),
                baseUrl,
                modelName,
                "api-key");
    }

    private BusinessChatExecutionPlan buildExecutionPlan(String modelName) {
        return new BusinessChatExecutionPlan(
                "请回答",
                "请回答",
                null,
                null,
                null,
                0,
                null,
                new BusinessChatFreshnessRequirement(false, "无需实时信息", List.of(), "NOT_REQUIRED"),
                "NOT_REQUIRED",
                List.of(),
                modelName,
                "open_ended_question_answer",
                "根据本轮输入生成执行计划。",
                BusinessChatAgentType.THINK_ACT,
                BusinessChatMode.OPEN_ENDED,
                BusinessChatClarificationPlan.notRequired(),
                List.of("执行模型：" + modelName));
    }

    private BusinessChatRuntimeContext buildRuntimeContext() {
        BusinessChatModelApiConfigSnapshot modelConfig =
                buildModelConfig("http://127.0.0.1:1", BusinessChatModelProvider.DASHSCOPE, "qwen-plus");
        BusinessChatTaskInfo taskInfo = new BusinessChatTaskInfo(
                1001L,
                2001L,
                "请回答",
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
        return new BusinessChatRuntimeContext(taskInfo, Sinks.many().unicast().onBackpressureBuffer());
    }

    private record CapturingOpenAiServer(HttpServer server, List<String> requestBodies) {

        static CapturingOpenAiServer start(boolean streaming) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            List<String> requestBodies = new ArrayList<>();
            server.createContext("/v1/chat/completions", exchange -> {
                requestBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                byte[] response = (streaming ? """
                        data: {"id":"chatcmpl-test","object":"chat.completion.chunk","choices":[{"index":0,"delta":{"content":"ok"},"finish_reason":null}]}

                        data: [DONE]

                        """ : """
                        {"id":"chatcmpl-test","object":"chat.completion","choices":[{"index":0,"message":{"role":"assistant","content":"ok"},"finish_reason":"stop"}]}
                        """).getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add(
                        "Content-Type",
                        streaming ? "text/event-stream" : "application/json");
                exchange.sendResponseHeaders(200, response.length);
                exchange.getResponseBody().write(response);
                exchange.close();
            });
            server.start();
            return new CapturingOpenAiServer(server, requestBodies);
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort();
        }

        void stop() {
            server.stop(0);
        }
    }
}
