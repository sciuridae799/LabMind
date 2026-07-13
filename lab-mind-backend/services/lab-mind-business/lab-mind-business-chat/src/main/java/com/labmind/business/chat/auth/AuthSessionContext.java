package com.labmind.business.chat.auth;

public record AuthSessionContext(
        String token,
        String userId,
        String account,
        String displayName,
        AuthRole role,
        String workspaceId,
        String workspaceName) {

    public boolean isSuperAdmin() {
        return role == AuthRole.SUPER_ADMIN;
    }

    public boolean canWriteDocuments() {
        return role == AuthRole.USER || role == AuthRole.SUPER_ADMIN;
    }
}
