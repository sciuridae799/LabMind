package com.labmind.business.chat.auth.service;

import com.labmind.business.chat.auth.AuthSessionContext;
import com.labmind.business.chat.auth.api.dto.AuthCreateUserRequest;
import com.labmind.business.chat.auth.api.dto.AuthCreateWorkspaceRequest;
import com.labmind.business.chat.auth.api.dto.AuthDeleteUserRequest;
import com.labmind.business.chat.auth.api.dto.AuthDeleteWorkspaceRequest;
import com.labmind.business.chat.auth.api.dto.AuthLoginRequest;
import com.labmind.business.chat.auth.api.dto.AuthSwitchWorkspaceRequest;
import com.labmind.business.chat.auth.api.dto.AuthUpdateUserRequest;
import com.labmind.business.chat.auth.api.dto.AuthUpdateWorkspaceRequest;
import com.labmind.business.chat.auth.api.vo.AuthSessionVo;
import com.labmind.business.chat.auth.api.vo.AuthUserAccountVo;
import com.labmind.business.chat.auth.api.vo.AuthWorkspaceVo;
import java.util.List;

public interface AuthService {

    AuthSessionVo login(AuthLoginRequest request);

    AuthSessionVo loginGuest();

    AuthSessionContext loadSession(String token);

    AuthSessionVo currentSession();

    AuthSessionVo switchWorkspace(AuthSwitchWorkspaceRequest request);

    void logout();

    List<AuthWorkspaceVo> listWorkspaces();

    AuthWorkspaceVo createWorkspace(AuthCreateWorkspaceRequest request);

    AuthWorkspaceVo updateWorkspace(AuthUpdateWorkspaceRequest request);

    void deleteWorkspace(AuthDeleteWorkspaceRequest request);

    List<AuthUserAccountVo> listUsers();

    AuthUserAccountVo createUser(AuthCreateUserRequest request);

    AuthUserAccountVo updateUser(AuthUpdateUserRequest request);

    void deleteUser(AuthDeleteUserRequest request);
}
