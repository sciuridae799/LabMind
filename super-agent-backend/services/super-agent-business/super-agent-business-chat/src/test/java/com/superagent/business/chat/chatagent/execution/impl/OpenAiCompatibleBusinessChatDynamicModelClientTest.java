package com.superagent.business.chat.chatagent.execution.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.superagent.business.chat.chatagent.config.BusinessChatRuntimeProperties;
import com.superagent.business.chat.chatagent.execution.agent.BusinessChatAgentType;
import com.superagent.business.chat.chatagent.orchestration.model.BusinessChatAgentStep;
import com.superagent.business.chat.chatagent.orchestration.model.BusinessChatClarificationPlan;
import com.superagent.business.chat.chatagent.execution.model.BusinessChatModelApiConfigSnapshot;
import com.superagent.business.chat.chatagent.orchestration.model.BusinessChatExecutionPlan;
import com.superagent.business.chat.chatagent.orchestration.model.BusinessChatFreshnessRequirement;
import com.superagent.business.chat.chatagent.orchestration.model.BusinessChatMode;
import com.superagent.business.chat.chatagent.execution.model.BusinessChatModelProvider;
import com.superagent.business.chat.chatagent.runtime.BusinessChatTaskInfo;
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
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
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
                mock(RedissonClient.class),
                mock(com.superagent.business.chat.chatagent.trace.BusinessChatUsageTraceService.class),
                new BusinessChatModelBusinessLogger());
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
                        "api-key", java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, 1000, "CNY"),
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
                        "api-key", java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, 1000, "CNY"),
                true);

        assertThat(options.getModel()).isEqualTo("qwen3-32b");
        assertThat(options.getExtraBody()).isNull();
    }

    @Test
    void shouldDisableDeepSeekThinkingForStreamingCalls() {
        var options = modelClient.buildChatOptions(
                new BusinessChatModelApiConfigSnapshot(
                        3001L,
                        BusinessChatModelProvider.DEEPSEEK,
                        "DeepSeek",
                        "https://api.deepseek.com",
                        "deepseek-v4-pro",
                        "api-key", java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, 1000, "CNY"),
                true);

        assertThat(options.getModel()).isEqualTo("deepseek-v4-pro");
        assertThat(options.getExtraBody())
                .containsEntry("thinking", java.util.Map.of("type", "disabled"))
                .doesNotContainKey("enable_thinking");
    }

    @Test
    void shouldDisableDeepSeekThinkingForNonStreamingCalls() {
        var options = modelClient.buildChatOptions(
                new BusinessChatModelApiConfigSnapshot(
                        3001L,
                        BusinessChatModelProvider.DEEPSEEK,
                        "DeepSeek",
                        "https://api.deepseek.com",
                        "deepseek-v4-pro",
                        "api-key", java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, 1000, "CNY"),
                false);

        assertThat(options.getModel()).isEqualTo("deepseek-v4-pro");
        assertThat(options.getExtraBody())
                .containsEntry("thinking", java.util.Map.of("type", "disabled"))
                .doesNotContainKey("enable_thinking");
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
                            .contains("\"stream_options\":{\"include_usage\":true}")
                            .doesNotContain("enable_thinking"));
        } finally {
            server.stop();
        }
    }

    @Test
    void shouldSendDeepSeekThinkingDisabledInStreamingHttpRequestBody() throws IOException {
        CapturingOpenAiServer server = CapturingOpenAiServer.start(true, "/chat/completions");
        try {
            List<String> content = modelClient.stream(
                            buildModelConfig(server.baseUrl(), BusinessChatModelProvider.DEEPSEEK, "deepseek-v4-pro"),
                            buildExecutionPlan("deepseek-v4-pro"))
                    .collectList()
                    .block(Duration.ofSeconds(5));

            assertThat(content).containsExactly("ok");
            assertThat(server.requestBodies()).singleElement()
                    .satisfies(body -> assertThat(body)
                            .contains("\"model\":\"deepseek-v4-pro\"")
                            .contains("\"stream\":true")
                            .contains("\"stream_options\":{\"include_usage\":true}")
                            .contains("\"thinking\":{\"type\":\"disabled\"}")
                            .doesNotContain("enable_thinking"));
        } finally {
            server.stop();
        }
    }

    @Test
    void shouldSendDeepSeekToolRequestWithoutThinkingOnlyFields() throws IOException {
        CapturingOpenAiServer server = CapturingOpenAiServer.start(true, "/chat/completions");
        try {
            ToolCallback toolCallback = FunctionToolCallback
                    .builder("tavily_search", (SearchRequest request) -> "ok")
                    .description("联网搜索工具")
                    .inputSchema("""
                            {
                              "type": "object",
                              "properties": {
                                "query": {
                                  "type": "string",
                                  "description": "需要联网搜索的问题或关键词"
                                }
                              },
                              "required": ["query"],
                              "additionalProperties": false
                            }
                            """)
                    .inputType(SearchRequest.class)
                    .build();

            List<String> content = modelClient.buildToolCallingStreamingChatClient(
                            buildModelConfig(server.baseUrl(), BusinessChatModelProvider.DEEPSEEK, "deepseek-v4-flash"))
                    .prompt()
                    .system("system")
                    .user("user")
                    .toolCallbacks(toolCallback)
                    .stream()
                    .content()
                    .collectList()
                    .block(Duration.ofSeconds(5));

            assertThat(content).containsExactly("ok");
            assertThat(server.requestBodies()).singleElement()
                    .satisfies(body -> assertThat(body)
                            .contains("\"model\":\"deepseek-chat\"")
                            .contains("\"stream\":true")
                            .contains("\"tools\"")
                            .doesNotContain("\"$schema\"")
                            .doesNotContain("\"thinking\"")
                            .doesNotContain("reasoning_effort")
                            .doesNotContain("enable_thinking"));
        } finally {
            server.stop();
        }
    }

    @Test
    void shouldUseZhipuCompletionPathWithoutOpenAiV1Prefix() throws IOException {
        CapturingOpenAiServer server = CapturingOpenAiServer.start(true, "/api/paas/v4/chat/completions");
        try {
            List<String> content = modelClient.stream(
                            buildModelConfig(
                                    server.baseUrl() + "/api/paas/v4",
                                    BusinessChatModelProvider.ZHIPU,
                                    "glm-5"),
                            buildExecutionPlan("glm-5"))
                    .collectList()
                    .block(Duration.ofSeconds(5));

            assertThat(content).containsExactly("ok");
            assertThat(server.requestBodies()).singleElement()
                    .satisfies(body -> assertThat(body)
                            .contains("\"model\":\"glm-5\"")
                            .contains("\"stream\":true"));
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
                mock(RedissonClient.class),
                mock(com.superagent.business.chat.chatagent.trace.BusinessChatUsageTraceService.class),
                new BusinessChatModelBusinessLogger());
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
                redissonClient,
                mock(com.superagent.business.chat.chatagent.trace.BusinessChatUsageTraceService.class),
                new BusinessChatModelBusinessLogger());
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
                "api-key", java.math.BigDecimal.ZERO, java.math.BigDecimal.ZERO, 1000, "CNY");
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
                null,
                List.of(),
                new BusinessChatFreshnessRequirement(false, "无需实时信息", List.of(), "NOT_REQUIRED"),
                "NOT_REQUIRED",
                List.of(),
                modelName,
                "open_ended_question_answer",
                "根据本轮输入生成执行计划。",
                List.of(agentStep(BusinessChatAgentType.THINK_ACT)),
                BusinessChatMode.OPEN_ENDED,
                BusinessChatClarificationPlan.notRequired(),
                false,
                null,
                List.of("执行模型：" + modelName));
    }

    private BusinessChatAgentStep agentStep(BusinessChatAgentType agentType) {
        return new BusinessChatAgentStep(
                agentType,
                "AGENT_" + agentType.getValue(),
                agentType.getDisplayName(),
                710,
                true);
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
            return start(streaming, "/v1/chat/completions");
        }

        static CapturingOpenAiServer start(boolean streaming, String completionsPath) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            List<String> requestBodies = new ArrayList<>();
            server.createContext(completionsPath, exchange -> {
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

    private record SearchRequest(String query) {
    }
}
