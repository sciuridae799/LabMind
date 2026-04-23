package com.superagent.business.chat.chatagent.model;

public final class BusinessChatConversationLeaseKeys {

    private BusinessChatConversationLeaseKeys() {
    }

    public static String conversationLeaseKey(String conversationId) {
        return "chat:conversation:running:%s".formatted(conversationId);
    }
}
