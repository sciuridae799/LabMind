package com.labmind.business.chat.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.labmind.business.chat.auth.AuthRole;
import com.labmind.business.chat.auth.AuthSessionContext;
import com.labmind.business.chat.auth.AuthSessionHolder;
import com.labmind.business.chat.auth.data.AuthWorkspaceData;
import com.labmind.business.chat.auth.mapper.AuthWorkspaceMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthWorkspaceScopeServiceImplTest {

    @Mock
    private AuthWorkspaceMapper workspaceMapper;

    @AfterEach
    void clearSession() {
        AuthSessionHolder.clear();
    }

    @Test
    void shouldReturnCanonicalWorkspaceIdForSuperAdminRequest() {
        AuthSessionHolder.set(new AuthSessionContext(
                "token-1",
                "1001",
                "admin",
                "Administrator",
                AuthRole.SUPER_ADMIN,
                "workspace-1",
                "Workspace One"));
        AuthWorkspaceData workspaceData = new AuthWorkspaceData();
        workspaceData.setWorkspaceId("public-demo");
        workspaceData.setStatus(1);
        when(workspaceMapper.selectOne(any())).thenReturn(workspaceData);

        AuthWorkspaceScopeServiceImpl service = new AuthWorkspaceScopeServiceImpl(workspaceMapper);

        assertThat(service.resolveReadableWorkspace("PUBLIC-DEMO")).isEqualTo("public-demo");
    }
}
