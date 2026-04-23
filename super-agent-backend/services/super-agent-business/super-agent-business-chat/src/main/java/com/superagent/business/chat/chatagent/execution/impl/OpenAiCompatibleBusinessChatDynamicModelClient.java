package com.superagent.business.chat.chatagent.execution.impl;

import com.superagent.business.chat.chatagent.execution.BusinessChatDynamicModelClient;
import com.superagent.business.chat.chatagent.model.BusinessChatExecutionPlan;
import com.superagent.business.chat.chatagent.model.BusinessChatModelApiConfigSnapshot;
import io.micrometer.observation.ObservationRegistry;
import java.util.Map;
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

@Service
public class OpenAiCompatibleBusinessChatDynamicModelClient implements BusinessChatDynamicModelClient {

    private final ToolCallingManager toolCallingManager;

    private final RetryTemplate retryTemplate;

    public OpenAiCompatibleBusinessChatDynamicModelClient(
            ToolCallingManager toolCallingManager,
            RetryTemplate retryTemplate) {
        this.toolCallingManager = toolCallingManager;
        this.retryTemplate = retryTemplate;
    }

    @Override
    public Flux<String> stream(BusinessChatModelApiConfigSnapshot modelConfig, BusinessChatExecutionPlan executionPlan) {
        return new AbstractChatClientBusinessChatModelClient(buildChatClient(modelConfig, true)) {
        }.stream(executionPlan);
    }

    @Override
    public String call(BusinessChatModelApiConfigSnapshot modelConfig, String systemPrompt, String userMessage) {
        return new AbstractChatClientBusinessChatModelClient(buildChatClient(modelConfig, false)) {
        }.call(systemPrompt, userMessage);
    }

    private ChatClient buildChatClient(BusinessChatModelApiConfigSnapshot modelConfig, boolean streaming) {
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
            builder.extraBody(Map.of("enable_thinking", false));
        }
        return builder.build();
    }
}
