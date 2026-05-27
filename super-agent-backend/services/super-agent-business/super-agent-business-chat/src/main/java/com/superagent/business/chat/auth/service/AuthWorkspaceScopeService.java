package com.superagent.business.chat.auth.service;

import com.superagent.business.chat.auth.AuthSessionContext;

public interface AuthWorkspaceScopeService {

    String resolveReadableWorkspace(String requestedWorkspaceId);

    String resolveWritableWorkspace(String requestedWorkspaceId);

    AuthSessionContext requireSuperAdmin();
}
