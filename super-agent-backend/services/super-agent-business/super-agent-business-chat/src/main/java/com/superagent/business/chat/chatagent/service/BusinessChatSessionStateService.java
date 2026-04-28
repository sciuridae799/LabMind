package com.superagent.business.chat.chatagent.service;

public interface BusinessChatSessionStateService {

    void activate(String conversationId);

    void clearIfActive(String conversationId);

    void clearActive();

    String getActiveConversationId();
}
