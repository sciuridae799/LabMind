package com.superagent.business.chat.auth.api.controller;

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
import com.superagent.business.chat.auth.service.AuthService;
import com.superagent.common.frame.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/auth/login")
    public ApiResponse<AuthSessionVo> login(@Valid @RequestBody AuthLoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }

    @PostMapping("/auth/guest-login")
    public ApiResponse<AuthSessionVo> loginGuest() {
        return ApiResponse.ok(authService.loginGuest());
    }

    @PostMapping("/auth/me")
    public ApiResponse<AuthSessionVo> currentSession() {
        return ApiResponse.ok(authService.currentSession());
    }

    @PostMapping("/auth/switch-workspace")
    public ApiResponse<AuthSessionVo> switchWorkspace(@Valid @RequestBody AuthSwitchWorkspaceRequest request) {
        return ApiResponse.ok(authService.switchWorkspace(request));
    }

    @PostMapping("/auth/logout")
    public ApiResponse<Void> logout() {
        authService.logout();
        return ApiResponse.ok();
    }

    @PostMapping("/admin/workspaces")
    public ApiResponse<List<AuthWorkspaceVo>> listWorkspaces() {
        return ApiResponse.ok(authService.listWorkspaces());
    }

    @PostMapping("/admin/workspaces/create")
    public ApiResponse<AuthWorkspaceVo> createWorkspace(@Valid @RequestBody AuthCreateWorkspaceRequest request) {
        return ApiResponse.ok(authService.createWorkspace(request));
    }

    @PostMapping("/admin/workspaces/update")
    public ApiResponse<AuthWorkspaceVo> updateWorkspace(@Valid @RequestBody AuthUpdateWorkspaceRequest request) {
        return ApiResponse.ok(authService.updateWorkspace(request));
    }

    @PostMapping("/admin/workspaces/delete")
    public ApiResponse<Void> deleteWorkspace(@Valid @RequestBody AuthDeleteWorkspaceRequest request) {
        authService.deleteWorkspace(request);
        return ApiResponse.ok();
    }

    @PostMapping("/admin/users")
    public ApiResponse<List<AuthUserAccountVo>> listUsers() {
        return ApiResponse.ok(authService.listUsers());
    }

    @PostMapping("/admin/users/create")
    public ApiResponse<AuthUserAccountVo> createUser(@Valid @RequestBody AuthCreateUserRequest request) {
        return ApiResponse.ok(authService.createUser(request));
    }

    @PostMapping("/admin/users/update")
    public ApiResponse<AuthUserAccountVo> updateUser(@Valid @RequestBody AuthUpdateUserRequest request) {
        return ApiResponse.ok(authService.updateUser(request));
    }

    @PostMapping("/admin/users/delete")
    public ApiResponse<Void> deleteUser(@Valid @RequestBody AuthDeleteUserRequest request) {
        authService.deleteUser(request);
        return ApiResponse.ok();
    }
}
