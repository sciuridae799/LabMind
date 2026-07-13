package com.labmind.business.chat.chatagent.execution.impl;

import com.labmind.business.chat.chatagent.config.BusinessChatRuntimeProperties;
import com.labmind.business.chat.chatagent.execution.BusinessChatDynamicModelClient;
import com.labmind.business.chat.chatagent.orchestration.model.BusinessChatExecutionPlan;
import com.labmind.business.chat.chatagent.execution.model.BusinessChatModelApiConfigSnapshot;
import com.labmind.business.chat.chatagent.execution.model.BusinessChatModelProvider;
import com.labmind.business.chat.chatagent.runtime.BusinessChatRuntimeContext;
import com.labmind.business.chat.chatagent.trace.BusinessChatUsageTraceService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.observation.ObservationRegistry;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.ai.chat.metadata.Usage;
import org.redisson.api.RedissonClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.model.tool.DefaultToolExecutionEligibilityPredicate;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

/**
 * OpenAI 兼容协议的动态模型客户端。
 *
 * <p>每次调用根据模型配置快照构建 ChatClient，让后台配置变更在下一轮问答或元数据生成中直接生效。</p>
 */
@Service
public class OpenAiCompatibleBusinessChatDynamicModelClient implements BusinessChatDynamicModelClient {

    private static final String THREAD_MODEL_CALL_COUNTER_KEY_PREFIX = "lab-mind:chat:model-calls:thread:";

    private static final String ZHIPU_CHAT_COMPLETIONS_PATH = "/chat/completions";

    private static final String DEEPSEEK_CHAT_COMPLETIONS_PATH = "/chat/completions";

    private static final String OPENAI_COMPATIBLE_CHAT_COMPLETIONS_PATH = "/v1/chat/completions";

    private static final String DEEPSEEK_NON_THINKING_TOOL_MODEL = "deepseek-chat";

    private static final String CALL_TYPE_STREAM = "STREAM";

    private static final String CALL_TYPE_NON_STREAM = "NON_STREAM";

    private static final int NON_STREAMING_RESPONSE_LOG_MAX_CHARS = 4000;

    private static final int JSON_OBJECT_RESPONSE_MAX_TOKENS = 512;

    private final ToolCallingManager toolCallingManager;

    private final RetryTemplate retryTemplate;

    private final BusinessChatRuntimeProperties runtimeProperties;

    private final RedissonClient redissonClient;

    private final BusinessChatUsageTraceService usageTraceService;

    private final BusinessChatModelBusinessLogger modelBusinessLogger;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public OpenAiCompatibleBusinessChatDynamicModelClient(
            ToolCallingManager toolCallingManager,
            RetryTemplate retryTemplate,
            BusinessChatRuntimeProperties runtimeProperties,
            RedissonClient redissonClient,
            BusinessChatUsageTraceService usageTraceService,
            BusinessChatModelBusinessLogger modelBusinessLogger) {
        this.toolCallingManager = toolCallingManager;
        this.retryTemplate = retryTemplate;
        this.runtimeProperties = runtimeProperties;
        this.redissonClient = redissonClient;
        this.usageTraceService = usageTraceService;
        this.modelBusinessLogger = modelBusinessLogger;
    }

    @Override
    public Flux<String> stream(BusinessChatModelApiConfigSnapshot modelConfig, BusinessChatExecutionPlan executionPlan) {
        AbstractChatClientBusinessChatModelClient client =
                new AbstractChatClientBusinessChatModelClient(buildChatClient(modelConfig, true, modelConfig.modelName())) {
                };
        BusinessChatModelPrompt prompt = client.buildPrompt(executionPlan);
        String callId = modelBusinessLogger.nextCallId();
        long startNanoTime = System.nanoTime();
        AtomicInteger streamChunkCount = new AtomicInteger();
        StringBuilder responseContent = new StringBuilder();
        return client.streamResponse(prompt)
                .map(response -> {
                    String text = client.extractText(response);
                    streamChunkCount.incrementAndGet();
                    responseContent.append(text);
                    return text;
                })
                .doOnComplete(() -> modelBusinessLogger.logCompleted(
                        callId,
                        null,
                        modelConfig,
                        CALL_TYPE_STREAM,
                        prompt,
                        responseContent.toString(),
                        null,
                        startNanoTime,
                        streamChunkCount.get()))
                .doOnError(error -> modelBusinessLogger.logFailed(
                        callId,
                        null,
                        modelConfig,
                        CALL_TYPE_STREAM,
                        prompt,
                        responseContent.toString(),
                        error,
                        startNanoTime,
                        streamChunkCount.get()));
    }

    @Override
    public Flux<String> stream(BusinessChatRuntimeContext runtimeContext, BusinessChatExecutionPlan executionPlan) {
        registerModelCall(runtimeContext);
        BusinessChatModelApiConfigSnapshot modelConfig = runtimeContext.getTaskInfo().modelConfig();
        Long traceId = usageTraceService.startModelCall(runtimeContext, modelConfig, CALL_TYPE_STREAM);
        AbstractChatClientBusinessChatModelClient client =
                new AbstractChatClientBusinessChatModelClient(buildChatClient(modelConfig, true, modelConfig.modelName())) {
                };
        BusinessChatModelPrompt prompt = client.buildPrompt(executionPlan);
        String callId = modelBusinessLogger.nextCallId();
        long startNanoTime = System.nanoTime();
        AtomicReference<Usage> usageRef = new AtomicReference<>();
        AtomicInteger streamChunkCount = new AtomicInteger();
        StringBuilder responseContent = new StringBuilder();
        return client.streamResponse(prompt)
                .doOnNext(response -> {
                    if (response.getMetadata() != null && response.getMetadata().getUsage() != null) {
                        usageRef.set(response.getMetadata().getUsage());
                    }
                })
                .map(response -> {
                    String text = client.extractText(response);
                    streamChunkCount.incrementAndGet();
                    responseContent.append(text);
                    return text;
                })
                .doOnComplete(() -> {
                    usageTraceService.completeModelCall(traceId, usageRef.get());
                    modelBusinessLogger.logCompleted(
                            callId,
                            runtimeContext,
                            modelConfig,
                            CALL_TYPE_STREAM,
                            prompt,
                            responseContent.toString(),
                            usageRef.get(),
                            startNanoTime,
                            streamChunkCount.get());
                })
                .doOnError(error -> {
                    usageTraceService.failModelCall(traceId, error);
                    modelBusinessLogger.logFailed(
                            callId,
                            runtimeContext,
                            modelConfig,
                            CALL_TYPE_STREAM,
                            prompt,
                            responseContent.toString(),
                            error,
                            startNanoTime,
                            streamChunkCount.get());
                });
    }

    @Override
    public String call(BusinessChatModelApiConfigSnapshot modelConfig, String systemPrompt, String userMessage) {
        BusinessChatModelPrompt prompt = new BusinessChatModelPrompt(systemPrompt, userMessage);
        String callId = modelBusinessLogger.nextCallId();
        long startNanoTime = System.nanoTime();
        try {
            NonStreamingModelCallResult response = callNonStreaming(modelConfig, prompt, false);
            modelBusinessLogger.logCompleted(
                    callId,
                    null,
                    modelConfig,
                    CALL_TYPE_NON_STREAM,
                    prompt,
                    response.content(),
                    response.usage(),
                    startNanoTime,
                    0);
            return response.content();
        } catch (NonStreamingModelResponseException error) {
            modelBusinessLogger.logFailed(
                    callId,
                    null,
                    modelConfig,
                    CALL_TYPE_NON_STREAM,
                    prompt,
                    error.responseBodyForLog(),
                    error,
                    startNanoTime,
                    0);
            throw error;
        } catch (RuntimeException error) {
            modelBusinessLogger.logFailed(
                    callId,
                    null,
                    modelConfig,
                    CALL_TYPE_NON_STREAM,
                    prompt,
                    "",
                    error,
                    startNanoTime,
                    0);
            throw error;
        }
    }

    @Override
    public String call(
            BusinessChatRuntimeContext runtimeContext,
            BusinessChatModelApiConfigSnapshot modelConfig,
            String systemPrompt,
            String userMessage) {
        return call(runtimeContext, modelConfig, systemPrompt, userMessage, false);
    }

    @Override
    public String callJsonObject(
            BusinessChatRuntimeContext runtimeContext,
            BusinessChatModelApiConfigSnapshot modelConfig,
            String systemPrompt,
            String userMessage) {
        return call(runtimeContext, modelConfig, systemPrompt, userMessage, true);
    }

    private String call(
            BusinessChatRuntimeContext runtimeContext,
            BusinessChatModelApiConfigSnapshot modelConfig,
            String systemPrompt,
            String userMessage,
            boolean jsonObjectResponseFormat) {
        registerModelCall(runtimeContext);
        Long traceId = usageTraceService.startModelCall(runtimeContext, modelConfig, CALL_TYPE_NON_STREAM);
        BusinessChatModelPrompt prompt = new BusinessChatModelPrompt(systemPrompt, userMessage);
        String callId = modelBusinessLogger.nextCallId();
        long startNanoTime = System.nanoTime();
        try {
            NonStreamingModelCallResult response = callNonStreaming(modelConfig, prompt, jsonObjectResponseFormat);
            usageTraceService.completeModelCall(traceId, response.usage());
            modelBusinessLogger.logCompleted(
                    callId,
                    runtimeContext,
                    modelConfig,
                    CALL_TYPE_NON_STREAM,
                    prompt,
                    response.content(),
                    response.usage(),
                    startNanoTime,
                    0);
            return response.content();
        } catch (NonStreamingModelResponseException error) {
            usageTraceService.failModelCall(traceId, error);
            modelBusinessLogger.logFailed(
                    callId,
                    runtimeContext,
                    modelConfig,
                    CALL_TYPE_NON_STREAM,
                    prompt,
                    error.responseBodyForLog(),
                    error,
                    startNanoTime,
                    0);
            throw error;
        } catch (RuntimeException error) {
            usageTraceService.failModelCall(traceId, error);
            modelBusinessLogger.logFailed(
                    callId,
                    runtimeContext,
                    modelConfig,
                    CALL_TYPE_NON_STREAM,
                    prompt,
                    "",
                    error,
                    startNanoTime,
                    0);
            throw error;
        }
    }

    private NonStreamingModelCallResult callNonStreaming(
            BusinessChatModelApiConfigSnapshot modelConfig,
            BusinessChatModelPrompt prompt,
            boolean jsonObjectResponseFormat) {
        return retryTemplate.execute(context -> executeNonStreamingCall(modelConfig, prompt, jsonObjectResponseFormat));
    }

    private NonStreamingModelCallResult executeNonStreamingCall(
            BusinessChatModelApiConfigSnapshot modelConfig,
            BusinessChatModelPrompt prompt,
            boolean jsonObjectResponseFormat) {
        String requestUrl = resolveChatCompletionsUrl(modelConfig);
        try {
            ResponseEntity<String> response = RestClient.create()
                    .post()
                    .uri(requestUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + modelConfig.apiKey())
                    .body(buildNonStreamingRequestBody(modelConfig, prompt, jsonObjectResponseFormat))
                    .retrieve()
                    .toEntity(String.class);
            NonStreamingHttpMetadata metadata = new NonStreamingHttpMetadata(
                    requestUrl,
                    response.getStatusCode().value(),
                    response.getHeaders().getContentType() == null
                            ? ""
                            : response.getHeaders().getContentType().toString());
            return parseNonStreamingResponse(response.getBody(), metadata);
        } catch (RestClientResponseException exception) {
            throw new NonStreamingModelResponseException(
                    "non-streaming model HTTP response was not successful: url=%s, status=%s, contentType=%s"
                            .formatted(
                                    requestUrl,
                                    exception.getStatusCode().value(),
                                    exception.getResponseHeaders() == null
                                            || exception.getResponseHeaders().getContentType() == null
                                            ? ""
                                            : exception.getResponseHeaders().getContentType()),
                    responseBodyForLog(requestUrl,
                            exception.getStatusCode().value(),
                            exception.getResponseHeaders() == null
                                    || exception.getResponseHeaders().getContentType() == null
                                    ? ""
                                    : exception.getResponseHeaders().getContentType().toString(),
                            exception.getResponseBodyAsString()),
                    exception);
        }
    }

    private Map<String, Object> buildNonStreamingRequestBody(
            BusinessChatModelApiConfigSnapshot modelConfig,
            BusinessChatModelPrompt prompt,
            boolean jsonObjectResponseFormat) {
        OpenAiChatOptions options = buildChatOptions(modelConfig, false, modelConfig.modelName());
        Map<String, Object> requestBody = new LinkedHashMap<>();
        requestBody.put("model", options.getModel());
        requestBody.put("messages", java.util.List.of(
                Map.of("role", "system", "content", prompt.systemPrompt()),
                Map.of("role", "user", "content", prompt.userPrompt())));
        requestBody.put("stream", false);
        if (options.getExtraBody() != null && !options.getExtraBody().isEmpty()) {
            requestBody.putAll(options.getExtraBody());
        }
        if (jsonObjectResponseFormat && modelConfig.provider() == BusinessChatModelProvider.DEEPSEEK) {
            requestBody.put("response_format", Map.of("type", "json_object"));
            requestBody.put("max_tokens", JSON_OBJECT_RESPONSE_MAX_TOKENS);
        }
        return requestBody;
    }

    private String resolveChatCompletionsUrl(BusinessChatModelApiConfigSnapshot modelConfig) {
        String baseUrl = modelConfig.baseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("model baseUrl must not be blank");
        }
        String path = switch (modelConfig.provider()) {
            case ZHIPU -> ZHIPU_CHAT_COMPLETIONS_PATH;
            case DEEPSEEK -> DEEPSEEK_CHAT_COMPLETIONS_PATH;
            case DASHSCOPE -> OPENAI_COMPATIBLE_CHAT_COMPLETIONS_PATH;
        };
        return baseUrl.strip().replaceAll("/+$", "") + path;
    }

    private NonStreamingModelCallResult parseNonStreamingResponse(
            String responseBody,
            NonStreamingHttpMetadata metadata) {
        if (responseBody == null || responseBody.isBlank()) {
            throw new NonStreamingModelResponseException(
                    "non-streaming model response body must not be blank: url=%s, status=%s, contentType=%s"
                            .formatted(metadata.requestUrl(), metadata.httpStatus(), metadata.contentType()),
                    responseBodyForLog(metadata, responseBody),
                    null);
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(responseBody);
        } catch (JsonProcessingException exception) {
            throw new NonStreamingModelResponseException(
                    "non-streaming model response body must be valid JSON: url=%s, status=%s, contentType=%s"
                            .formatted(metadata.requestUrl(), metadata.httpStatus(), metadata.contentType()),
                    responseBodyForLog(metadata, responseBody),
                    exception);
        }
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            throw new IllegalStateException("non-streaming model response must contain choices");
        }
        JsonNode contentNode = choices.get(0).path("message").path("content");
        if (!contentNode.isTextual()) {
            throw new IllegalStateException("non-streaming model response must contain choices[0].message.content");
        }
        return new NonStreamingModelCallResult(contentNode.asText(), parseUsage(root.path("usage")));
    }

    private Usage parseUsage(JsonNode usageNode) {
        if (!usageNode.isObject()) {
            return null;
        }
        Integer promptTokens = readInteger(usageNode.path("prompt_tokens"));
        Integer completionTokens = readInteger(usageNode.path("completion_tokens"));
        Integer totalTokens = readInteger(usageNode.path("total_tokens"));
        return new NonStreamingUsage(promptTokens, completionTokens, totalTokens, usageNode);
    }

    private Integer readInteger(JsonNode node) {
        return node.isInt() || node.isLong() ? node.asInt() : null;
    }

    private String responseBodyForLog(NonStreamingHttpMetadata metadata, String responseBody) {
        return responseBodyForLog(metadata.requestUrl(), metadata.httpStatus(), metadata.contentType(), responseBody);
    }

    private String responseBodyForLog(String requestUrl, int httpStatus, String contentType, String responseBody) {
        StringBuilder builder = new StringBuilder();
        builder.append("requestUrl=").append(requestUrl).append("\n");
        builder.append("httpStatus=").append(httpStatus).append("\n");
        builder.append("contentType=").append(contentType == null ? "" : contentType).append("\n");
        builder.append("responseBody=\n");
        if (responseBody == null) {
            return builder.toString();
        }
        String normalizedResponseBody = responseBody.strip();
        if (normalizedResponseBody.length() <= NON_STREAMING_RESPONSE_LOG_MAX_CHARS) {
            return builder.append(normalizedResponseBody).toString();
        }
        return builder.append(normalizedResponseBody, 0, NON_STREAMING_RESPONSE_LOG_MAX_CHARS).toString();
    }

    private record NonStreamingModelCallResult(String content, Usage usage) {
    }

    private record NonStreamingHttpMetadata(String requestUrl, int httpStatus, String contentType) {
    }

    private static class NonStreamingModelResponseException extends IllegalStateException {

        private final String responseBodyForLog;

        private NonStreamingModelResponseException(String message, String responseBodyForLog, Throwable cause) {
            super(message, cause);
            this.responseBodyForLog = responseBodyForLog;
        }

        private String responseBodyForLog() {
            return responseBodyForLog;
        }
    }

    private record NonStreamingUsage(
            Integer promptTokens,
            Integer completionTokens,
            Integer totalTokens,
            Object nativeUsage) implements Usage {

        @Override
        public Integer getPromptTokens() {
            return promptTokens;
        }

        @Override
        public Integer getCompletionTokens() {
            return completionTokens;
        }

        @Override
        public Integer getTotalTokens() {
            return totalTokens;
        }

        @Override
        public Object getNativeUsage() {
            return nativeUsage;
        }
    }

    private void registerModelCall(BusinessChatRuntimeContext runtimeContext) {
        long runCount = runtimeContext.incrementModelCallCount();
        if (runCount > runtimeProperties.getMaxModelCallsPerRun()) {
            throw new IllegalStateException("model call limit exceeded for current run: " + runCount);
        }
        String conversationId = runtimeContext.getTaskInfo().conversationId();
        long threadCount = redissonClient.getAtomicLong(THREAD_MODEL_CALL_COUNTER_KEY_PREFIX + conversationId)
                .incrementAndGet();
        if (threadCount > runtimeProperties.getMaxModelCallsPerThread()) {
            throw new IllegalStateException("model call limit exceeded for conversation: " + conversationId);
        }
    }

    private ChatClient buildChatClient(
            BusinessChatModelApiConfigSnapshot modelConfig,
            boolean streaming,
            String modelName) {
        // 每次调用按模型配置创建客户端，保证后台切换 baseUrl/apiKey/model 后下一轮立即生效。
        OpenAiApi.Builder openAiApiBuilder = OpenAiApi.builder()
                .baseUrl(modelConfig.baseUrl())
                .apiKey(new SimpleApiKey(modelConfig.apiKey()))
                .restClientBuilder(RestClient.builder())
                .webClientBuilder(WebClient.builder());
        if (modelConfig.provider() == BusinessChatModelProvider.ZHIPU) {
            openAiApiBuilder.completionsPath(ZHIPU_CHAT_COMPLETIONS_PATH);
        } else if (modelConfig.provider() == BusinessChatModelProvider.DEEPSEEK) {
            openAiApiBuilder.completionsPath(DEEPSEEK_CHAT_COMPLETIONS_PATH);
        }
        OpenAiApi openAiApi = openAiApiBuilder.build();
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(buildChatOptions(modelConfig, streaming, modelName))
                .toolCallingManager(toolCallingManager)
                .retryTemplate(retryTemplate)
                .observationRegistry(ObservationRegistry.NOOP)
                .toolExecutionEligibilityPredicate(new DefaultToolExecutionEligibilityPredicate())
                .build();
        return ChatClient.builder(chatModel).build();
    }

    ChatClient buildToolCallingStreamingChatClient(BusinessChatModelApiConfigSnapshot modelConfig) {
        return buildChatClient(modelConfig, true, resolveToolCallingModelName(modelConfig));
    }

    OpenAiChatOptions buildChatOptions(BusinessChatModelApiConfigSnapshot modelConfig, boolean streaming) {
        return buildChatOptions(modelConfig, streaming, modelConfig.modelName());
    }

    OpenAiChatOptions buildChatOptions(
            BusinessChatModelApiConfigSnapshot modelConfig,
            boolean streaming,
            String modelName) {
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder()
                .model(modelName);
        if (streaming) {
            builder.streamUsage(true);
        }
        if (modelConfig.provider() == BusinessChatModelProvider.DEEPSEEK
                && !DEEPSEEK_NON_THINKING_TOOL_MODEL.equals(modelName)) {
            builder.extraBody(Map.of("thinking", Map.of("type", "disabled")));
        } else if (!streaming) {
            // 收尾和画像生成要求稳定 JSON，非流式调用关闭 thinking，避免模型把思考内容混进结构化输出。
            builder.extraBody(Map.of("enable_thinking", false));
        }
        return builder.build();
    }

    private String resolveToolCallingModelName(BusinessChatModelApiConfigSnapshot modelConfig) {
        if (modelConfig.provider() == BusinessChatModelProvider.DEEPSEEK) {
            return DEEPSEEK_NON_THINKING_TOOL_MODEL;
        }
        return modelConfig.modelName();
    }
}
