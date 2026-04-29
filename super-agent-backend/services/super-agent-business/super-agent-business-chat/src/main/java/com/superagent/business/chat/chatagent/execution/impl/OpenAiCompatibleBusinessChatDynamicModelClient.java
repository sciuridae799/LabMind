package com.superagent.business.chat.chatagent.execution.impl;

import com.superagent.business.chat.chatagent.config.BusinessChatRuntimeProperties;
import com.superagent.business.chat.chatagent.execution.BusinessChatDynamicModelClient;
import com.superagent.business.chat.chatagent.model.BusinessChatExecutionPlan;
import com.superagent.business.chat.chatagent.model.BusinessChatModelApiConfigSnapshot;
import com.superagent.business.chat.chatagent.runtime.BusinessChatRuntimeContext;
import io.micrometer.observation.ObservationRegistry;
import java.util.Map;
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

    private final ToolCallingManager toolCallingManager;

    private final RetryTemplate retryTemplate;

    private final BusinessChatRuntimeProperties runtimeProperties;

    private final RedissonClient redissonClient;

    public OpenAiCompatibleBusinessChatDynamicModelClient(
            ToolCallingManager toolCallingManager,
            RetryTemplate retryTemplate,
            BusinessChatRuntimeProperties runtimeProperties,
            RedissonClient redissonClient) {
        this.toolCallingManager = toolCallingManager;
        this.retryTemplate = retryTemplate;
        this.runtimeProperties = runtimeProperties;
        this.redissonClient = redissonClient;
    }

    @Override
    public Flux<String> stream(BusinessChatModelApiConfigSnapshot modelConfig, BusinessChatExecutionPlan executionPlan) {
        return new AbstractChatClientBusinessChatModelClient(buildChatClient(modelConfig, true)) {
        }.stream(executionPlan);
    }

    @Override
    public Flux<String> stream(BusinessChatRuntimeContext runtimeContext, BusinessChatExecutionPlan executionPlan) {
        registerModelCall(runtimeContext);
        return stream(runtimeContext.getTaskInfo().modelConfig(), executionPlan);
    }

    @Override
    public String call(BusinessChatModelApiConfigSnapshot modelConfig, String systemPrompt, String userMessage) {
        return new AbstractChatClientBusinessChatModelClient(buildChatClient(modelConfig, false)) {
        }.call(systemPrompt, userMessage);
    }

    @Override
    public String call(
            BusinessChatRuntimeContext runtimeContext,
            BusinessChatModelApiConfigSnapshot modelConfig,
            String systemPrompt,
            String userMessage) {
        registerModelCall(runtimeContext);
        return call(modelConfig, systemPrompt, userMessage);
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

    private ChatClient buildChatClient(BusinessChatModelApiConfigSnapshot modelConfig, boolean streaming) {
        // 每次调用按模型配置创建客户端，保证后台切换 baseUrl/apiKey/model 后下一轮立即生效。
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(modelConfig.baseUrl())
                .apiKey(new SimpleApiKey(modelConfig.apiKey()))
                .restClientBuilder(RestClient.builder())
                .webClientBuilder(WebClient.builder())
                .build();
        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(buildChatOptions(modelConfig, streaming))
                .toolCallingManager(toolCallingManager)
                .retryTemplate(retryTemplate)
                .observationRegistry(ObservationRegistry.NOOP)
                .toolExecutionEligibilityPredicate(new DefaultToolExecutionEligibilityPredicate())
                .build();
        return ChatClient.builder(chatModel).build();
    }

    OpenAiChatOptions buildChatOptions(BusinessChatModelApiConfigSnapshot modelConfig, boolean streaming) {
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder()
                .model(modelConfig.modelName());
        if (!streaming) {
            // 收尾和画像生成要求稳定 JSON，非流式调用关闭 thinking，避免模型把思考内容混进结构化输出。
            builder.extraBody(Map.of("enable_thinking", false));
        }
        return builder.build();
    }
}
