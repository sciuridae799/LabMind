package com.labmind.business.chat.chatagent.runtime;

public final class BusinessChatConversationLeaseKeys {

    private BusinessChatConversationLeaseKeys() {
    }

    public static String conversationLeaseKey(String conversationId) {
        return "chat:conversation:running:%s".formatted(conversationId);
    }
}
