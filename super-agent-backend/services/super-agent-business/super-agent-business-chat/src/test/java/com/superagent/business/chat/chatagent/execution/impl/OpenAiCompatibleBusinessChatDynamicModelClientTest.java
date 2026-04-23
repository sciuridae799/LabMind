package com.superagent.business.chat.chatagent.execution.impl;

import static org.assertj.core.api.Assertions.assertThat;

import com.superagent.business.chat.chatagent.agent.BusinessChatAgentType;
import com.superagent.business.chat.chatagent.model.BusinessChatModelApiConfigSnapshot;
import com.superagent.business.chat.chatagent.model.BusinessChatExecutionPlan;
import com.superagent.business.chat.chatagent.model.BusinessChatFreshnessRequirement;
import com.superagent.business.chat.chatagent.model.BusinessChatMode;
import com.superagent.business.chat.chatagent.model.BusinessChatModelProvider;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.retry.support.RetryTemplate;

class OpenAiCompatibleBusinessChatDynamicModelClientTest {

    private OpenAiCompatibleBusinessChatDynamicModelClient modelClient;

    @BeforeEach
    void setUp() {
        modelClient = new OpenAiCompatibleBusinessChatDynamicModelClient(
                ToolCallingManager.builder().build(),
                RetryTemplate.defaultInstance());
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
                0,
                new BusinessChatFreshnessRequirement(false, "无需实时信息", List.of(), "NOT_REQUIRED"),
                "NOT_REQUIRED",
                modelName,
                "open_ended_question_answer",
                "根据本轮输入生成执行计划。",
                BusinessChatAgentType.THINK_ACT,
                BusinessChatMode.OPEN_ENDED,
                List.of("执行模型：" + modelName));
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
