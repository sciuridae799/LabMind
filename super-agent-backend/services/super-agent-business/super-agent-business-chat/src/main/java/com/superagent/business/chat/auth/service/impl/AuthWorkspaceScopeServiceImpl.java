package com.superagent.business.chat.auth.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.superagent.business.chat.auth.AuthErrorCode;
import com.superagent.business.chat.auth.AuthException;
import com.superagent.business.chat.auth.AuthRole;
import com.superagent.business.chat.auth.AuthSessionContext;
import com.superagent.business.chat.auth.AuthSessionHolder;
import com.superagent.business.chat.auth.data.AuthWorkspaceData;
import com.superagent.business.chat.auth.mapper.AuthWorkspaceMapper;
import com.superagent.business.chat.auth.service.AuthWorkspaceScopeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AuthWorkspaceScopeServiceImpl implements AuthWorkspaceScopeService {

    private static final int NORMAL_STATUS = 1;

    private final AuthWorkspaceMapper workspaceMapper;

    @Override
    public String resolveReadableWorkspace(String requestedWorkspaceId) {
        AuthSessionContext session = AuthSessionHolder.required();
        String normalizedRequestedWorkspaceId = normalizeOptionalWorkspaceId(requestedWorkspaceId);
        if (!session.isSuperAdmin()) {
            if (StringUtils.hasText(normalizedRequestedWorkspaceId)
                    && !session.workspaceId().equals(normalizedRequestedWorkspaceId)) {
                throw new AuthException(AuthErrorCode.AUTH_FORBIDDEN, "当前账号不能访问其他工作组资料");
            }
            return session.workspaceId();
        }
        String workspaceId = StringUtils.hasText(normalizedRequestedWorkspaceId)
                ? normalizedRequestedWorkspaceId
                : session.workspaceId();
        return requireWorkspaceExists(workspaceId);
    }

    @Override
    public String resolveWritableWorkspace(String requestedWorkspaceId) {
        AuthSessionContext session = AuthSessionHolder.required();
        if (session.role() == AuthRole.GUEST) {
            throw new AuthException(AuthErrorCode.AUTH_FORBIDDEN, "访客不能上传、编辑或删除实验室资料");
        }
        return resolveReadableWorkspace(requestedWorkspaceId);
    }

    @Override
    public AuthSessionContext requireSuperAdmin() {
        AuthSessionContext session = AuthSessionHolder.required();
        if (!session.isSuperAdmin()) {
            throw new AuthException(AuthErrorCode.AUTH_FORBIDDEN, "只有超级管理员可以访问该后台功能");
        }
        return session;
    }

    private String normalizeOptionalWorkspaceId(String workspaceId) {
        String normalized = workspaceId == null ? null : workspaceId.strip();
        return StringUtils.hasText(normalized) ? normalized : null;
    }

    private String requireWorkspaceExists(String workspaceId) {
        AuthWorkspaceData workspaceData = workspaceMapper.selectOne(
                Wrappers.<AuthWorkspaceData>lambdaQuery()
                        .eq(AuthWorkspaceData::getWorkspaceId, workspaceId)
                        .eq(AuthWorkspaceData::getStatus, NORMAL_STATUS)
                        .last("limit 1"));
        if (workspaceData == null) {
            throw new AuthException(AuthErrorCode.AUTH_FORBIDDEN, "工作组不存在或不可用：" + workspaceId);
        }
        return workspaceData.getWorkspaceId();
    }
}
