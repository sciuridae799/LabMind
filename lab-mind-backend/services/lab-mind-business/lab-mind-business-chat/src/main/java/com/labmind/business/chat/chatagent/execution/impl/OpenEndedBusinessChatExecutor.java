package com.labmind.business.chat.chatagent.execution.impl;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.modelcalllimit.ModelCallLimitHook;
import com.alibaba.cloud.ai.graph.agent.hook.toolcalllimit.ToolCallLimitHook;
import com.alibaba.cloud.ai.graph.agent.interceptor.toolerror.ToolErrorInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.toolretry.ToolRetryInterceptor;
import com.alibaba.cloud.ai.graph.checkpoint.savers.mysql.MysqlSaver;
import com.alibaba.cloud.ai.toolcalling.tavily.TavilySearchService;
import com.labmind.business.chat.chatagent.config.BusinessChatRuntimeProperties;
import com.labmind.business.chat.chatagent.execution.BusinessChatExecutor;
import com.labmind.business.chat.chatagent.logging.BusinessChatToolBusinessLogger;
import com.labmind.business.chat.chatagent.orchestration.model.BusinessChatExecutionPlan;
import com.labmind.business.chat.chatagent.orchestration.model.BusinessChatMode;
import com.labmind.business.chat.chatagent.runtime.BusinessChatAgentCounterKeys;
import com.labmind.business.chat.chatagent.runtime.BusinessChatRuntimeContext;
import com.labmind.business.chat.chatagent.trace.BusinessChatUsageTraceService;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.function.Function;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RedissonClient;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.SynchronousSink;

@Service
public class OpenEndedBusinessChatExecutor implements BusinessChatExecutor {

    private static final String TAVILY_TOOL_NAME = "tavily_search";

    private static final String TAVILY_TOOL_INPUT_SCHEMA = """
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
            """;

    private static final String MODEL_THREAD_CONTEXT_KEY = "__model_call_limit_thread_count__";

    private static final String MODEL_RUN_CONTEXT_KEY = "__model_call_limit_run_count__";

    private static final String TAVILY_THREAD_CONTEXT_KEY =
            "__tool_call_limit_thread_count___" + TAVILY_TOOL_NAME;

    private static final String TAVILY_RUN_CONTEXT_KEY =
            "__tool_call_limit_run_count___" + TAVILY_TOOL_NAME;

    private final OpenAiCompatibleBusinessChatDynamicModelClient modelClient;

    private final BusinessChatRuntimeProperties runtimeProperties;

    private final TavilySearchService tavilySearchService;

    private final RedissonClient redissonClient;

    private final MysqlSaver mysqlSaver;

    private final ExecutorService toolExecutor;

    private final BusinessChatUsageTraceService usageTraceService;

    private final BusinessChatModelBusinessLogger modelBusinessLogger;

    private final BusinessChatToolBusinessLogger toolBusinessLogger;

    public OpenEndedBusinessChatExecutor(
            OpenAiCompatibleBusinessChatDynamicModelClient modelClient,
            BusinessChatRuntimeProperties runtimeProperties,
            TavilySearchService tavilySearchService,
            RedissonClient redissonClient,
            MysqlSaver mysqlSaver,
            BusinessChatUsageTraceService usageTraceService,
            BusinessChatModelBusinessLogger modelBusinessLogger,
            BusinessChatToolBusinessLogger toolBusinessLogger,
            @Qualifier("businessChatToolExecutor") ExecutorService toolExecutor) {
        this.modelClient = modelClient;
        this.runtimeProperties = runtimeProperties;
        this.tavilySearchService = tavilySearchService;
        this.redissonClient = redissonClient;
        this.usageTraceService = usageTraceService;
        this.modelBusinessLogger = modelBusinessLogger;
        this.toolBusinessLogger = toolBusinessLogger;
        this.mysqlSaver = mysqlSaver;
        this.toolExecutor = toolExecutor;
    }

    @Override
    public BusinessChatMode executionMode() {
        return BusinessChatMode.OPEN_ENDED;
    }

    @Override
    public Flux<String> execute(BusinessChatRuntimeContext runtimeContext, BusinessChatExecutionPlan executionPlan) {
        RunnableConfig runnableConfig = buildRunnableConfig(runtimeContext);
        ReactAgent agent = buildAgent(runtimeContext, executionPlan);
        BusinessChatModelPrompt prompt = buildReactAgentPrompt(executionPlan);
        String callId = modelBusinessLogger.nextCallId();
        long startNanoTime = System.nanoTime();
        String stageCode = runtimeContext.getCurrentTraceStageCode();
        String stageName = runtimeContext.getCurrentTraceStageName();
        StringBuilder responseContent = new StringBuilder();
        return Flux.defer(() -> {
                    try {
                        return agent.streamMessages(executionPlan.rewrittenQuestion(), runnableConfig);
                    } catch (Exception error) {
                        return Flux.error(error);
                    }
                })
                .<String>handle((message, sink) -> emitTextDelta(message, sink, responseContent))
                .doOnComplete(() -> modelBusinessLogger.logCompleted(
                        callId,
                        runtimeContext,
                        runtimeContext.getTaskInfo().modelConfig(),
                        "REACT_AGENT_STREAM",
                        prompt,
                        responseContent.toString(),
                        null,
                        startNanoTime,
                        0,
                        stageCode,
                        stageName))
                .doOnError(error -> modelBusinessLogger.logFailed(
                        callId,
                        runtimeContext,
                        runtimeContext.getTaskInfo().modelConfig(),
                        "REACT_AGENT_STREAM",
                        prompt,
                        responseContent.toString(),
                        error,
                        startNanoTime,
                        0,
                        stageCode,
                        stageName))
                .doFinally(signalType -> syncAgentCounters(runtimeContext, runnableConfig));
    }

    private RunnableConfig buildRunnableConfig(BusinessChatRuntimeContext runtimeContext) {
        String conversationId = runtimeContext.getTaskInfo().conversationId();
        RunnableConfig runnableConfig = RunnableConfig.builder()
                .threadId(conversationId)
                .build();
        runnableConfig.context().put(
                MODEL_THREAD_CONTEXT_KEY,
                toInt(redissonClient.getAtomicLong(
                        BusinessChatAgentCounterKeys.modelThreadCounterKey(conversationId)).get()));
        runnableConfig.context().put(MODEL_RUN_CONTEXT_KEY, 0);
        runnableConfig.context().put(
                TAVILY_THREAD_CONTEXT_KEY,
                toInt(redissonClient.getAtomicLong(
                        BusinessChatAgentCounterKeys.toolThreadCounterKey(conversationId, TAVILY_TOOL_NAME)).get()));
        runnableConfig.context().put(TAVILY_RUN_CONTEXT_KEY, 0);
        return runnableConfig;
    }

    private ReactAgent buildAgent(
            BusinessChatRuntimeContext runtimeContext,
            BusinessChatExecutionPlan executionPlan) {
        ChatClient chatClient = modelClient.buildToolCallingStreamingChatClient(runtimeContext.getTaskInfo().modelConfig());
        Function<TavilySearchService.Request, TavilySearchService.Response> tracedTavilySearch =
                request -> tracedTavilySearch(runtimeContext, request);
        ToolCallback tavilyTool = FunctionToolCallback
                .builder(TAVILY_TOOL_NAME, tracedTavilySearch)
                .description("联网搜索工具。只在开放式问题需要实时信息、外部事实核验或多步检索时使用。")
                .inputSchema(TAVILY_TOOL_INPUT_SCHEMA)
                .inputType(TavilySearchService.Request.class)
                .build();
        return ReactAgent.builder()
                .name("open_ended_react_agent")
                .description("开放式问题联网搜索 Agent")
                .chatClient(chatClient)
                .instruction(buildInstruction(executionPlan))
                .tools(List.of(tavilyTool))
                .saver(mysqlSaver)
                .hooks(
                        ModelCallLimitHook.builder()
                                .runLimit(runtimeProperties.getMaxModelCallsPerRun())
                                .threadLimit(runtimeProperties.getMaxModelCallsPerThread())
                                .exitBehavior(ModelCallLimitHook.ExitBehavior.ERROR)
                                .build(),
                        ToolCallLimitHook.builder()
                                .toolName(TAVILY_TOOL_NAME)
                                .runLimit(runtimeProperties.getMaxTavilyToolCallsPerRun())
                                .threadLimit(runtimeProperties.getMaxTavilyToolCallsPerThread())
                                .exitBehavior(ToolCallLimitHook.ExitBehavior.ERROR)
                                .build())
                .interceptors(
                        ToolRetryInterceptor.builder()
                                .toolName(TAVILY_TOOL_NAME)
                                .maxRetries(runtimeProperties.getTavilyMaxRetries())
                                .initialDelay(runtimeProperties.getTavilyRetryInitialDelayMs())
                                .maxDelay(runtimeProperties.getTavilyRetryMaxDelayMs())
                                .backoffFactor(2D)
                                .jitter(true)
                                .build(),
                        ToolErrorInterceptor.builder().build())
                .parallelToolExecution(true)
                .maxParallelTools(runtimeProperties.getMaxParallelTools())
                .toolExecutionTimeout(Duration.ofSeconds(20))
                .executor(toolExecutor)
                .build();
    }

    private String buildInstruction(BusinessChatExecutionPlan executionPlan) {
        StringBuilder builder = new StringBuilder()
                .append("你是开放式问题执行器，只处理已经经过业务编排确认的 OPEN_ENDED 问题。\n")
                .append("回答前先判断是否需要外部事实、实时信息或多步搜索；需要时使用 tavily_search，不需要时直接回答。\n")
                .append("不要替代知识库问答或当前文档问答；如果问题明显要求内部知识库材料，应说明本轮执行模式不包含知识库检索。\n")
                .append("联网搜索结果必须转化为可核验的中文回答，区分事实、推断和不确定信息。\n")
                .append("如果 tavily_search 失败、超过调用上限或没有足够证据，不要编造搜索结果，必须明确说明无法确认实时信息。\n\n")
                .append("原始问题：").append(executionPlan.originalQuestion()).append("\n")
                .append("改写问题：").append(executionPlan.rewrittenQuestion()).append("\n")
                .append("时效性判断：").append(executionPlan.freshnessRequirement().reason()).append("\n");
        if (StringUtils.hasText(executionPlan.answerHistoryContextText())) {
            builder.append("历史上下文：\n")
                    .append(executionPlan.answerHistoryContextText())
                    .append("\n");
        }
        return builder.toString();
    }

    private BusinessChatModelPrompt buildReactAgentPrompt(BusinessChatExecutionPlan executionPlan) {
        return new BusinessChatModelPrompt(buildInstruction(executionPlan), executionPlan.rewrittenQuestion());
    }

    private void emitTextDelta(Message message, SynchronousSink<String> sink, StringBuilder responseContent) {
        String text = message.getText();
        if (StringUtils.hasText(text)) {
            responseContent.append(text);
            sink.next(text);
        }
    }

    private void syncAgentCounters(BusinessChatRuntimeContext runtimeContext, RunnableConfig runnableConfig) {
        int modelRunCount = intContextValue(runnableConfig, MODEL_RUN_CONTEXT_KEY);
        for (int index = 0; index < modelRunCount; index++) {
            runtimeContext.incrementModelCallCount();
        }
        String conversationId = runtimeContext.getTaskInfo().conversationId();
        syncThreadCounter(
                BusinessChatAgentCounterKeys.modelThreadCounterKey(conversationId),
                intContextValue(runnableConfig, MODEL_THREAD_CONTEXT_KEY));
        syncThreadCounter(
                BusinessChatAgentCounterKeys.toolThreadCounterKey(conversationId, TAVILY_TOOL_NAME),
                intContextValue(runnableConfig, TAVILY_THREAD_CONTEXT_KEY));
    }

    private void syncThreadCounter(String key, int value) {
        RAtomicLong atomicLong = redissonClient.getAtomicLong(key);
        if (atomicLong.get() < value) {
            atomicLong.set(value);
        }
    }

    private int intContextValue(RunnableConfig runnableConfig, String key) {
        Object value = runnableConfig.context().get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    private int toInt(long value) {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) value;
    }

    private TavilySearchService.Response tracedTavilySearch(
            BusinessChatRuntimeContext runtimeContext,
            TavilySearchService.Request request) {
        Long traceId = usageTraceService.startToolCall(runtimeContext, TAVILY_TOOL_NAME);
        String callId = toolBusinessLogger.nextCallId();
        long startNanoTime = System.nanoTime();
        try {
            TavilySearchService.Response response = tavilySearchService.apply(request);
            usageTraceService.completeToolCall(traceId);
            toolBusinessLogger.logCompleted(
                    callId,
                    runtimeContext,
                    TAVILY_TOOL_NAME,
                    renderTavilyRequest(request),
                    renderTavilyResponse(response),
                    startNanoTime);
            return response;
        } catch (RuntimeException error) {
            usageTraceService.failToolCall(traceId, error);
            toolBusinessLogger.logFailed(
                    callId,
                    runtimeContext,
                    TAVILY_TOOL_NAME,
                    renderTavilyRequest(request),
                    error,
                    startNanoTime);
            throw error;
        }
    }

    private String renderTavilyRequest(TavilySearchService.Request request) {
        return "query=" + request.query();
    }

    private String renderTavilyResponse(TavilySearchService.Response response) {
        StringBuilder builder = new StringBuilder();
        builder.append("query=").append(response.query()).append("\n");
        builder.append("responseTime=").append(response.responseTime()).append("\n");
        if (response.results() == null || response.results().isEmpty()) {
            builder.append("results=none");
            return builder.toString();
        }
        for (int index = 0; index < response.results().size(); index++) {
            var result = response.results().get(index);
            builder.append("\n[").append(index + 1).append("] ")
                    .append(result.title()).append("\n")
                    .append("url=").append(result.url()).append("\n")
                    .append("score=").append(result.score()).append("\n")
                    .append("content=").append(result.content()).append("\n");
        }
        return builder.toString();
    }
}
