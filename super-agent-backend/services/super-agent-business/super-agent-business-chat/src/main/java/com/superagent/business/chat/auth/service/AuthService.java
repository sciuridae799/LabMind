package com.superagent.business.chat.auth.service;

import com.superagent.business.chat.auth.AuthSessionContext;
import com.superagent.business.chat.auth.api.dto.AuthCreateUserRequest;
import com.superagent.business.chat.auth.api.dto.AuthCreateWorkspaceRequest;
import com.superagent.business.chat.auth.api.dto.AuthDeleteUserRequest;
import com.superagent.business.chat.auth.api.dto.AuthDeleteWorkspaceRequest;
import com.superagent.business.chat.auth.api.dto.AuthLoginRequest;
import com.superagent.business.chat.auth.api.dto.AuthSwitchWorkspaceRequest;
import com.superagent.business.chat.auth.api.dto.AuthUpdateUserRequest;
import com.superagent.business.chat.auth.api.dto.AuthUpdateWorkspaceRequest;
import com.superagent.business.chat.auth.api.vo.AuthSessionVo;
import com.superagent.business.chat.auth.api.vo.AuthUserAccountVo;
import com.superagent.business.chat.auth.api.vo.AuthWorkspaceVo;
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
