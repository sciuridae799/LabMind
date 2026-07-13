package com.labmind.business.chat.chatagent.runtime;

public final class BusinessChatAgentCounterKeys {

    private static final String MODEL_THREAD_COUNTER_KEY_PREFIX = "lab-mind:chat:model-calls:thread:";

    private static final String TOOL_THREAD_COUNTER_KEY_PREFIX = "lab-mind:chat:tool-calls:thread:";

    private static final String TAVILY_TOOL_NAME = "tavily_search";

    private BusinessChatAgentCounterKeys() {
    }

    public static String modelThreadCounterKey(String conversationId) {
        return MODEL_THREAD_COUNTER_KEY_PREFIX + conversationId;
    }

    public static String toolThreadCounterKey(String conversationId, String toolName) {
        return TOOL_THREAD_COUNTER_KEY_PREFIX + conversationId + ":" + toolName;
    }

    public static String[] conversationCounterKeys(String conversationId) {
        return new String[] {
                modelThreadCounterKey(conversationId),
                toolThreadCounterKey(conversationId, TAVILY_TOOL_NAME)
        };
    }
}
