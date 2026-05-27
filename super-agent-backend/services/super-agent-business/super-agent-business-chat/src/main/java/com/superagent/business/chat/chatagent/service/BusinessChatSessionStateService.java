package com.superagent.business.chat.chatagent.service;

public interface BusinessChatSessionStateService {

    void activate(String conversationId, String workspaceId, String authSessionToken);

    void clearIfActive(String conversationId, String workspaceId, String authSessionToken);

    void clearActive(String workspaceId, String authSessionToken);

    String getActiveConversationId(String workspaceId, String authSessionToken);
}
