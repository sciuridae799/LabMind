package com.superagent.business.chat.chatagent.execution.impl;

import com.superagent.business.chat.chatagent.config.BusinessChatRuntimeProperties;
import com.superagent.business.chat.chatagent.execution.BusinessChatDynamicModelClient;
import com.superagent.business.chat.chatagent.orchestration.model.BusinessChatExecutionPlan;
import com.superagent.business.chat.chatagent.execution.model.BusinessChatModelApiConfigSnapshot;
import com.superagent.business.chat.chatagent.execution.model.BusinessChatModelProvider;
import com.superagent.business.chat.chatagent.runtime.BusinessChatRuntimeContext;
import com.superagent.business.chat.chatagent.trace.BusinessChatUsageTraceService;
import io.micrometer.observation.ObservationRegistry;
import java.util.Map;
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
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;

/**
 * OpenAI 兼容协议的动态模型客户端。
 *
 * <p>每次调用根据模型配置快照构建 ChatClient，让后台配置变更在下一轮问答或元数据生成中直接生效。</p>
 */
@Service
public class OpenAiCompatibleBusinessChatDynamicModelClient implements BusinessChatDynamicModelClient {

    private static final String THREAD_MODEL_CALL_COUNTER_KEY_PREFIX = "super-agent:chat:model-calls:thread:";

    private static final String ZHIPU_CHAT_COMPLETIONS_PATH = "/chat/completions";

    private static final String DEEPSEEK_CHAT_COMPLETIONS_PATH = "/chat/completions";

    private static final String DEEPSEEK_NON_THINKING_TOOL_MODEL = "deepseek-chat";

    private static final String CALL_TYPE_STREAM = "STREAM";

    private static final String CALL_TYPE_NON_STREAM = "NON_STREAM";

    private final ToolCallingManager toolCallingManager;

    private final RetryTemplate retryTemplate;

    private final BusinessChatRuntimeProperties runtimeProperties;

    private final RedissonClient redissonClient;

    private final BusinessChatUsageTraceService usageTraceService;

    public OpenAiCompatibleBusinessChatDynamicModelClient(
            ToolCallingManager toolCallingManager,
            RetryTemplate retryTemplate,
            BusinessChatRuntimeProperties runtimeProperties,
            RedissonClient redissonClient,
            BusinessChatUsageTraceService usageTraceService) {
        this.toolCallingManager = toolCallingManager;
        this.retryTemplate = retryTemplate;
        this.runtimeProperties = runtimeProperties;
        this.redissonClient = redissonClient;
        this.usageTraceService = usageTraceService;
    }

    @Override
    public Flux<String> stream(BusinessChatModelApiConfigSnapshot modelConfig, BusinessChatExecutionPlan executionPlan) {
        return new AbstractChatClientBusinessChatModelClient(buildChatClient(modelConfig, true, modelConfig.modelName())) {
        }.stream(executionPlan);
    }

    @Override
    public Flux<String> stream(BusinessChatRuntimeContext runtimeContext, BusinessChatExecutionPlan executionPlan) {
        registerModelCall(runtimeContext);
        BusinessChatModelApiConfigSnapshot modelConfig = runtimeContext.getTaskInfo().modelConfig();
        Long traceId = usageTraceService.startModelCall(runtimeContext, modelConfig, CALL_TYPE_STREAM);
        AbstractChatClientBusinessChatModelClient client =
                new AbstractChatClientBusinessChatModelClient(buildChatClient(modelConfig, true, modelConfig.modelName())) {
                };
        AtomicReference<Usage> usageRef = new AtomicReference<>();
        return client.streamResponse(executionPlan)
                .doOnNext(response -> {
                    if (response.getMetadata() != null && response.getMetadata().getUsage() != null) {
                        usageRef.set(response.getMetadata().getUsage());
                    }
                })
                .map(client::extractText)
                .doOnComplete(() -> usageTraceService.completeModelCall(traceId, usageRef.get()))
                .doOnError(error -> usageTraceService.failModelCall(traceId, error));
    }

    @Override
    public String call(BusinessChatModelApiConfigSnapshot modelConfig, String systemPrompt, String userMessage) {
        return new AbstractChatClientBusinessChatModelClient(buildChatClient(modelConfig, false, modelConfig.modelName())) {
        }.call(systemPrompt, userMessage);
    }

    @Override
    public String call(
            BusinessChatRuntimeContext runtimeContext,
            BusinessChatModelApiConfigSnapshot modelConfig,
            String systemPrompt,
            String userMessage) {
        registerModelCall(runtimeContext);
        Long traceId = usageTraceService.startModelCall(runtimeContext, modelConfig, CALL_TYPE_NON_STREAM);
        try {
            AbstractChatClientBusinessChatModelClient client =
                    new AbstractChatClientBusinessChatModelClient(buildChatClient(modelConfig, false, modelConfig.modelName())) {
                    };
            var response = client.callResponse(systemPrompt, userMessage);
            usageTraceService.completeModelCall(
                    traceId,
                    response.getMetadata() == null ? null : response.getMetadata().getUsage());
            return client.extractText(response);
        } catch (RuntimeException error) {
            usageTraceService.failModelCall(traceId, error);
            throw error;
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
