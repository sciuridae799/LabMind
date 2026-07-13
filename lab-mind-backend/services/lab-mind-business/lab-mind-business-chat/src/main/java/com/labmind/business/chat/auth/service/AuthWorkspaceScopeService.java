package com.labmind.business.chat.auth.service;

import com.labmind.business.chat.auth.AuthSessionContext;

public interface AuthWorkspaceScopeService {

    String resolveReadableWorkspace(String requestedWorkspaceId);

    String resolveWritableWorkspace(String requestedWorkspaceId);

    AuthSessionContext requireSuperAdmin();
}
