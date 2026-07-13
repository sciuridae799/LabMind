package com.labmind.business.chat.auth.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.labmind.business.chat.auth.AuthRole;
import com.labmind.business.chat.auth.data.AuthUserAccountData;
import com.labmind.business.chat.auth.data.AuthUserWorkspaceData;
import com.labmind.business.chat.auth.data.AuthWorkspaceData;
import com.labmind.business.chat.auth.mapper.AuthUserAccountMapper;
import com.labmind.business.chat.auth.mapper.AuthUserWorkspaceMapper;
import com.labmind.business.chat.auth.mapper.AuthWorkspaceMapper;
import com.labmind.business.chat.auth.support.AuthPasswordHasher;
import com.labmind.idgenerator.toolkit.SnowflakeIdGenerator;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AuthBootstrapInitializerTest {

    @Mock
    private AuthWorkspaceMapper workspaceMapper;

    @Mock
    private AuthUserAccountMapper userAccountMapper;

    @Mock
    private AuthUserWorkspaceMapper userWorkspaceMapper;

    @Mock
    private AuthPasswordHasher passwordHasher;

    @Mock
    private SnowflakeIdGenerator snowflakeIdGenerator;

    private AuthBootstrapProperties properties;
    private AuthBootstrapInitializer initializer;

    @BeforeEach
    void setUp() {
        properties = new AuthBootstrapProperties();
        properties.setSuperAdminAccount("bootstrap-admin");
        properties.setSuperAdminPassword("configured-password");
        properties.setSuperAdminDisplayName("Bootstrap Administrator");
        properties.setWorkspaceId("workspace-1");
        properties.setWorkspaceName("Workspace");
        initializer = new AuthBootstrapInitializer(
                properties,
                workspaceMapper,
                userAccountMapper,
                userWorkspaceMapper,
                passwordHasher,
                snowflakeIdGenerator);
        lenient().when(workspaceMapper.selectByWorkspaceIdForUpdate("public-demo"))
                .thenReturn(workspace("public-demo", 1));
    }

    @ParameterizedTest
    @MethodSource("unavailableConfiguredAccountStates")
    void shouldRestoreConfiguredAccountWhenNoAvailableSuperAdmin(
            AuthRole currentRole,
            int currentEnabled,
            int currentStatus) {
        AuthUserAccountData configuredAccount = user(currentRole, currentEnabled, currentStatus);
        AuthUserWorkspaceData deletedRelation = relation(0);
        when(userAccountMapper.selectAvailableSuperAdminIdsForUpdate("super_admin", 1, 1))
                .thenReturn(List.of());
        when(workspaceMapper.selectByWorkspaceIdForUpdate("workspace-1")).thenReturn(workspace());
        when(userAccountMapper.selectByAccountForUpdate("bootstrap-admin")).thenReturn(configuredAccount);
        when(passwordHasher.newSalt()).thenReturn("new-salt");
        when(passwordHasher.hash("configured-password", "new-salt")).thenReturn("new-hash");
        when(userWorkspaceMapper.selectOne(any())).thenReturn(deletedRelation);

        initializer.run(null);

        assertThat(configuredAccount.getDisplayName()).isEqualTo("Bootstrap Administrator");
        assertThat(configuredAccount.getPasswordSalt()).isEqualTo("new-salt");
        assertThat(configuredAccount.getPasswordHash()).isEqualTo("new-hash");
        assertThat(configuredAccount.getRole()).isEqualTo(AuthRole.SUPER_ADMIN.value());
        assertThat(configuredAccount.getWorkspaceId()).isEqualTo("workspace-1");
        assertThat(configuredAccount.getEnabled()).isEqualTo(1);
        assertThat(configuredAccount.getStatus()).isEqualTo(1);
        assertThat(deletedRelation.getStatus()).isEqualTo(1);
        verify(userAccountMapper).updateById(configuredAccount);
        verify(userAccountMapper, never()).insert(any(AuthUserAccountData.class));
        verify(userWorkspaceMapper).updateById(deletedRelation);

        InOrder restorationOrder = inOrder(userAccountMapper);
        restorationOrder.verify(userAccountMapper)
                .selectAvailableSuperAdminIdsForUpdate("super_admin", 1, 1);
        restorationOrder.verify(userAccountMapper).selectByAccountForUpdate("bootstrap-admin");
        restorationOrder.verify(userAccountMapper).updateById(configuredAccount);
    }

    @Test
    void shouldLeaveBootstrapAccountUntouchedWhenAvailableSuperAdminExists() {
        when(userAccountMapper.selectAvailableSuperAdminIdsForUpdate("super_admin", 1, 1))
                .thenReturn(List.of(1001L));

        initializer.run(null);

        verify(userAccountMapper, never()).selectByAccountForUpdate(any());
        verify(workspaceMapper).selectByWorkspaceIdForUpdate("public-demo");
        verifyNoInteractions(userWorkspaceMapper, passwordHasher, snowflakeIdGenerator);
    }

    @Test
    void shouldCreateConfiguredSuperAdminWhenAccountDoesNotExist() {
        when(userAccountMapper.selectAvailableSuperAdminIdsForUpdate("super_admin", 1, 1))
                .thenReturn(List.of());
        when(workspaceMapper.selectByWorkspaceIdForUpdate("workspace-1")).thenReturn(workspace());
        when(userAccountMapper.selectByAccountForUpdate("bootstrap-admin")).thenReturn(null);
        when(passwordHasher.newSalt()).thenReturn("new-salt");
        when(passwordHasher.hash("configured-password", "new-salt")).thenReturn("new-hash");
        when(snowflakeIdGenerator.nextId()).thenReturn(1001L, 3001L);
        when(userWorkspaceMapper.selectOne(any())).thenReturn(null);

        initializer.run(null);

        ArgumentCaptor<AuthUserAccountData> accountCaptor = ArgumentCaptor.forClass(AuthUserAccountData.class);
        verify(userAccountMapper).insert(accountCaptor.capture());
        AuthUserAccountData createdAccount = accountCaptor.getValue();
        assertThat(createdAccount.getId()).isEqualTo(1001L);
        assertThat(createdAccount.getAccount()).isEqualTo("bootstrap-admin");
        assertThat(createdAccount.getRole()).isEqualTo(AuthRole.SUPER_ADMIN.value());
        assertThat(createdAccount.getEnabled()).isEqualTo(1);
        assertThat(createdAccount.getStatus()).isEqualTo(1);

        ArgumentCaptor<AuthUserWorkspaceData> relationCaptor =
                ArgumentCaptor.forClass(AuthUserWorkspaceData.class);
        verify(userWorkspaceMapper).insert(relationCaptor.capture());
        assertThat(relationCaptor.getValue().getId()).isEqualTo(3001L);
        assertThat(relationCaptor.getValue().getUserId()).isEqualTo(1001L);
        assertThat(relationCaptor.getValue().getWorkspaceId()).isEqualTo("workspace-1");
        assertThat(relationCaptor.getValue().getStatus()).isEqualTo(1);
    }

    @Test
    void shouldRestoreConfiguredWorkspaceWhenItWasSoftDeleted() {
        AuthWorkspaceData deletedWorkspace = workspace("workspace-1", 0);
        deletedWorkspace.setWorkspaceName("Deleted workspace");
        AuthUserAccountData configuredAccount = user(AuthRole.SUPER_ADMIN, 0, 1);
        when(userAccountMapper.selectAvailableSuperAdminIdsForUpdate("super_admin", 1, 1))
                .thenReturn(List.of());
        when(workspaceMapper.selectByWorkspaceIdForUpdate("workspace-1")).thenReturn(deletedWorkspace);
        when(userAccountMapper.selectByAccountForUpdate("bootstrap-admin")).thenReturn(configuredAccount);
        when(passwordHasher.newSalt()).thenReturn("new-salt");
        when(passwordHasher.hash("configured-password", "new-salt")).thenReturn("new-hash");
        when(userWorkspaceMapper.selectOne(any())).thenReturn(relation(1));

        initializer.run(null);

        assertThat(deletedWorkspace.getWorkspaceName()).isEqualTo("Workspace");
        assertThat(deletedWorkspace.getStatus()).isEqualTo(1);
        verify(workspaceMapper).updateById(deletedWorkspace);
        verify(workspaceMapper, never()).insert(any(AuthWorkspaceData.class));
    }

    private static Stream<Arguments> unavailableConfiguredAccountStates() {
        return Stream.of(
                Arguments.of(AuthRole.SUPER_ADMIN, 0, 1),
                Arguments.of(AuthRole.USER, 1, 1),
                Arguments.of(AuthRole.SUPER_ADMIN, 0, 0));
    }

    private AuthUserAccountData user(AuthRole role, int enabled, int status) {
        AuthUserAccountData data = new AuthUserAccountData();
        data.setId(1001L);
        data.setAccount("bootstrap-admin");
        data.setDisplayName("Old name");
        data.setPasswordSalt("old-salt");
        data.setPasswordHash("old-hash");
        data.setRole(role.value());
        data.setWorkspaceId("old-workspace");
        data.setEnabled(enabled);
        data.setStatus(status);
        return data;
    }

    private AuthWorkspaceData workspace() {
        return workspace("workspace-1", 1);
    }

    private AuthWorkspaceData workspace(String workspaceId, int status) {
        AuthWorkspaceData data = new AuthWorkspaceData();
        data.setId(2001L);
        data.setWorkspaceId(workspaceId);
        data.setWorkspaceName("Workspace");
        data.setStatus(status);
        return data;
    }

    private AuthUserWorkspaceData relation(int status) {
        AuthUserWorkspaceData data = new AuthUserWorkspaceData();
        data.setId(3001L);
        data.setUserId(1001L);
        data.setWorkspaceId("workspace-1");
        data.setStatus(status);
        return data;
    }
}
