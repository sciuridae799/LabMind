package com.labmind.business.chat.auth.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.labmind.business.chat.auth.AuthRole;
import com.labmind.business.chat.auth.api.dto.AuthDeleteUserRequest;
import com.labmind.business.chat.auth.api.dto.AuthDeleteWorkspaceRequest;
import com.labmind.business.chat.auth.api.dto.AuthUpdateUserRequest;
import com.labmind.business.chat.auth.data.AuthSessionData;
import com.labmind.business.chat.auth.data.AuthUserAccountData;
import com.labmind.business.chat.auth.data.AuthUserWorkspaceData;
import com.labmind.business.chat.auth.data.AuthWorkspaceData;
import com.labmind.business.chat.auth.mapper.AuthSessionMapper;
import com.labmind.business.chat.auth.mapper.AuthUserAccountMapper;
import com.labmind.business.chat.auth.mapper.AuthUserWorkspaceMapper;
import com.labmind.business.chat.auth.mapper.AuthWorkspaceMapper;
import com.labmind.business.chat.auth.service.AuthWorkspaceScopeService;
import com.labmind.business.chat.auth.support.AuthPasswordHasher;
import com.labmind.business.chat.chatagent.persistence.mapper.BusinessChatDialogueMapper;
import com.labmind.business.chat.knowledge.document.mapper.KnowledgeDocumentMapper;
import com.labmind.common.frame.enums.BaseCode;
import com.labmind.common.frame.exception.BaseException;
import com.labmind.idgenerator.toolkit.SnowflakeIdGenerator;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock
    private AuthUserAccountMapper userAccountMapper;

    @Mock
    private AuthUserWorkspaceMapper userWorkspaceMapper;

    @Mock
    private AuthWorkspaceMapper workspaceMapper;

    @Mock
    private AuthSessionMapper sessionMapper;

    @Mock
    private KnowledgeDocumentMapper knowledgeDocumentMapper;

    @Mock
    private BusinessChatDialogueMapper businessChatDialogueMapper;

    @Mock
    private AuthWorkspaceScopeService workspaceScopeService;

    @Mock
    private AuthPasswordHasher passwordHasher;

    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        MapperBuilderAssistant assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "test");
        TableInfoHelper.initTableInfo(assistant, AuthSessionData.class);
        TableInfoHelper.initTableInfo(assistant, AuthUserAccountData.class);
        TableInfoHelper.initTableInfo(assistant, AuthUserWorkspaceData.class);
        TableInfoHelper.initTableInfo(assistant, AuthWorkspaceData.class);
        authService = new AuthServiceImpl(
                userAccountMapper,
                userWorkspaceMapper,
                workspaceMapper,
                sessionMapper,
                knowledgeDocumentMapper,
                businessChatDialogueMapper,
                workspaceScopeService,
                passwordHasher,
                snowflakeIdGenerator);
    }

    @Test
    void shouldRejectDeletingReservedGuestWorkspace() {
        when(workspaceMapper.selectOne(any())).thenReturn(workspace("public-demo"));
        AuthDeleteWorkspaceRequest request = new AuthDeleteWorkspaceRequest();
        request.setWorkspaceId("public-demo");

        assertThatThrownBy(() -> authService.deleteWorkspace(request))
                .isInstanceOf(BaseException.class)
                .hasMessage("reserved guest workspace cannot be deleted: public-demo")
                .extracting("code")
                .isEqualTo(BaseCode.INVALID_PARAMETER.getCode());

        verify(workspaceScopeService).requireSuperAdmin();
        verify(workspaceMapper).selectOne(any());
        verify(workspaceMapper, never()).update(any(), any());
    }

    @Test
    void shouldRejectDeletingReservedGuestWorkspaceWithDifferentCase() {
        when(workspaceMapper.selectOne(any())).thenReturn(workspace("public-demo"));
        AuthDeleteWorkspaceRequest request = new AuthDeleteWorkspaceRequest();
        request.setWorkspaceId("PUBLIC-DEMO");

        assertThatThrownBy(() -> authService.deleteWorkspace(request))
                .isInstanceOf(BaseException.class)
                .hasMessage("reserved guest workspace cannot be deleted: public-demo")
                .extracting("code")
                .isEqualTo(BaseCode.INVALID_PARAMETER.getCode());

        verify(workspaceScopeService).requireSuperAdmin();
        verify(workspaceMapper).selectOne(any());
        verify(workspaceMapper, never()).update(any(), any());
        verify(userAccountMapper, never()).selectCount(any());
        verify(userWorkspaceMapper, never()).selectCount(any());
        verify(knowledgeDocumentMapper, never()).selectCount(any());
        verify(businessChatDialogueMapper, never()).selectCount(any());
    }

    @Test
    void shouldRejectDisablingOnlyAvailableSuperAdmin() {
        AuthUserAccountData admin = user(1001L, AuthRole.SUPER_ADMIN, 1);
        when(workspaceMapper.selectList(any())).thenReturn(List.of(workspace("workspace-1")));
        when(userAccountMapper.selectOne(any())).thenReturn(admin);
        when(userAccountMapper.selectAvailableSuperAdminIdsForUpdate("super_admin", 1, 1))
                .thenReturn(List.of(admin.getId()));

        AuthUpdateUserRequest request = updateRequest(admin.getId(), AuthRole.SUPER_ADMIN, false);

        assertLastAvailableSuperAdminRejected(() -> authService.updateUser(request));

        verify(userAccountMapper).selectAvailableSuperAdminIdsForUpdate("super_admin", 1, 1);
        verify(userAccountMapper, never()).update(any(), any());
        verify(userWorkspaceMapper, never()).update(any(), any());
        verify(sessionMapper, never()).update(any(), any());
    }

    @Test
    void shouldRejectDowngradingOnlyAvailableSuperAdmin() {
        AuthUserAccountData admin = user(1001L, AuthRole.SUPER_ADMIN, 1);
        when(workspaceMapper.selectList(any())).thenReturn(List.of(workspace("workspace-1")));
        when(userAccountMapper.selectOne(any())).thenReturn(admin);
        when(userAccountMapper.selectAvailableSuperAdminIdsForUpdate("super_admin", 1, 1))
                .thenReturn(List.of(admin.getId()));

        AuthUpdateUserRequest request = updateRequest(admin.getId(), AuthRole.USER, true);

        assertLastAvailableSuperAdminRejected(() -> authService.updateUser(request));

        verify(userAccountMapper).selectAvailableSuperAdminIdsForUpdate("super_admin", 1, 1);
        verify(userAccountMapper, never()).update(any(), any());
    }

    @Test
    void shouldRejectDeletingOnlyAvailableSuperAdmin() {
        AuthUserAccountData admin = user(1001L, AuthRole.SUPER_ADMIN, 1);
        when(userAccountMapper.selectOne(any())).thenReturn(admin);
        when(userAccountMapper.selectAvailableSuperAdminIdsForUpdate("super_admin", 1, 1))
                .thenReturn(List.of(admin.getId()));
        AuthDeleteUserRequest request = new AuthDeleteUserRequest();
        request.setUserId(String.valueOf(admin.getId()));

        assertLastAvailableSuperAdminRejected(() -> authService.deleteUser(request));

        verify(userAccountMapper).selectAvailableSuperAdminIdsForUpdate("super_admin", 1, 1);
        verify(userAccountMapper, never()).update(any(), any());
        verify(userWorkspaceMapper, never()).update(any(), any());
        verify(sessionMapper, never()).update(any(), any());
    }

    @Test
    void shouldLockAvailableSuperAdminsBeforeDowngradingWhenAnotherAdminExists() {
        AuthUserAccountData admin = user(1001L, AuthRole.SUPER_ADMIN, 1);
        when(workspaceMapper.selectList(any())).thenReturn(List.of(workspace("workspace-1")));
        when(userAccountMapper.selectOne(any())).thenReturn(admin);
        when(userAccountMapper.selectAvailableSuperAdminIdsForUpdate("super_admin", 1, 1))
                .thenReturn(List.of(admin.getId(), 1002L));
        when(userWorkspaceMapper.selectList(any())).thenReturn(List.of());

        AuthUpdateUserRequest request = updateRequest(admin.getId(), AuthRole.USER, true);

        assertThat(authService.updateUser(request).getRole()).isEqualTo(AuthRole.USER.value());

        InOrder userMutationOrder = inOrder(userAccountMapper);
        userMutationOrder.verify(userAccountMapper).selectOne(any());
        userMutationOrder.verify(userAccountMapper)
                .selectAvailableSuperAdminIdsForUpdate("super_admin", 1, 1);
        userMutationOrder.verify(userAccountMapper).update(any(), any());
    }

    private void assertLastAvailableSuperAdminRejected(Runnable operation) {
        assertThatThrownBy(operation::run)
                .isInstanceOf(BaseException.class)
                .hasMessage("at least one enabled super_admin account is required")
                .extracting("code")
                .isEqualTo(BaseCode.INVALID_PARAMETER.getCode());
    }

    private AuthUpdateUserRequest updateRequest(long userId, AuthRole role, boolean enabled) {
        AuthUpdateUserRequest request = new AuthUpdateUserRequest();
        request.setUserId(String.valueOf(userId));
        request.setDisplayName("Administrator");
        request.setRole(role.value());
        request.setEnabled(enabled);
        request.setWorkspaceIds(List.of("workspace-1"));
        return request;
    }

    private AuthUserAccountData user(long id, AuthRole role, int enabled) {
        AuthUserAccountData data = new AuthUserAccountData();
        data.setId(id);
        data.setAccount("admin-" + id);
        data.setDisplayName("Administrator");
        data.setRole(role.value());
        data.setWorkspaceId("workspace-1");
        data.setEnabled(enabled);
        data.setStatus(1);
        return data;
    }

    private AuthWorkspaceData workspace(String workspaceId) {
        AuthWorkspaceData data = new AuthWorkspaceData();
        data.setId(2001L);
        data.setWorkspaceId(workspaceId);
        data.setWorkspaceName("Workspace");
        data.setStatus(1);
        return data;
    }
}
