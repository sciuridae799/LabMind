package com.superagent.business.chat.auth.config;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.superagent.business.chat.auth.AuthRole;
import com.superagent.business.chat.auth.data.AuthUserAccountData;
import com.superagent.business.chat.auth.data.AuthUserWorkspaceData;
import com.superagent.business.chat.auth.data.AuthWorkspaceData;
import com.superagent.business.chat.auth.mapper.AuthUserAccountMapper;
import com.superagent.business.chat.auth.mapper.AuthUserWorkspaceMapper;
import com.superagent.business.chat.auth.mapper.AuthWorkspaceMapper;
import com.superagent.business.chat.auth.support.AuthPasswordHasher;
import com.superagent.idgenerator.toolkit.SnowflakeIdGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class AuthBootstrapInitializer implements ApplicationRunner {

    private static final int NORMAL_STATUS = 1;
    private static final int ENABLED = 1;
    private static final String BOOTSTRAP_LOCK_WORKSPACE_ID = "public-demo";

    private final AuthBootstrapProperties properties;
    private final AuthWorkspaceMapper workspaceMapper;
    private final AuthUserAccountMapper userAccountMapper;
    private final AuthUserWorkspaceMapper userWorkspaceMapper;
    private final AuthPasswordHasher passwordHasher;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!hasAnyBootstrapConfig()) {
            return;
        }
        requireFullBootstrapConfig();
        requireBootstrapLock();
        if (!userAccountMapper.selectAvailableSuperAdminIdsForUpdate(
                AuthRole.SUPER_ADMIN.value(), ENABLED, NORMAL_STATUS).isEmpty()) {
            return;
        }
        String workspaceId = properties.getWorkspaceId().strip();
        AuthWorkspaceData workspaceData = workspaceMapper.selectByWorkspaceIdForUpdate(workspaceId);
        if (workspaceData == null) {
            workspaceData = new AuthWorkspaceData();
            workspaceData.setId(snowflakeIdGenerator.nextId());
            workspaceData.setWorkspaceId(workspaceId);
            workspaceData.setWorkspaceName(properties.getWorkspaceName().strip());
            workspaceData.setStatus(NORMAL_STATUS);
            workspaceMapper.insert(workspaceData);
        } else if (!Integer.valueOf(NORMAL_STATUS).equals(workspaceData.getStatus())) {
            workspaceData.setWorkspaceName(properties.getWorkspaceName().strip());
            workspaceData.setStatus(NORMAL_STATUS);
            workspaceMapper.updateById(workspaceData);
        }

        String account = properties.getSuperAdminAccount().strip();
        AuthUserAccountData userData = userAccountMapper.selectByAccountForUpdate(account);
        boolean existingAccount = userData != null;
        String salt = passwordHasher.newSalt();
        if (userData == null) {
            userData = new AuthUserAccountData();
            userData.setId(snowflakeIdGenerator.nextId());
            userData.setAccount(account);
        }
        userData.setDisplayName(properties.getSuperAdminDisplayName().strip());
        userData.setPasswordSalt(salt);
        userData.setPasswordHash(passwordHasher.hash(properties.getSuperAdminPassword(), salt));
        userData.setRole(AuthRole.SUPER_ADMIN.value());
        userData.setWorkspaceId(workspaceData.getWorkspaceId());
        userData.setEnabled(ENABLED);
        userData.setStatus(NORMAL_STATUS);
        if (existingAccount) {
            userAccountMapper.updateById(userData);
        } else {
            userAccountMapper.insert(userData);
        }

        restoreWorkspaceRelation(userData.getId(), workspaceData.getWorkspaceId());
    }

    private boolean hasAnyBootstrapConfig() {
        return StringUtils.hasText(properties.getSuperAdminAccount())
                || StringUtils.hasText(properties.getSuperAdminPassword())
                || StringUtils.hasText(properties.getSuperAdminDisplayName())
                || StringUtils.hasText(properties.getWorkspaceId())
                || StringUtils.hasText(properties.getWorkspaceName());
    }

    private void requireFullBootstrapConfig() {
        requireText(properties.getSuperAdminAccount(), "super-agent.auth.bootstrap.super-admin-account");
        requireText(properties.getSuperAdminPassword(), "super-agent.auth.bootstrap.super-admin-password");
        requireText(properties.getSuperAdminDisplayName(), "super-agent.auth.bootstrap.super-admin-display-name");
        requireText(properties.getWorkspaceId(), "super-agent.auth.bootstrap.workspace-id");
        requireText(properties.getWorkspaceName(), "super-agent.auth.bootstrap.workspace-name");
    }

    private void requireText(String value, String propertyName) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(propertyName + " is required when auth bootstrap is configured.");
        }
    }

    private void requireBootstrapLock() {
        AuthWorkspaceData lockWorkspace = workspaceMapper.selectByWorkspaceIdForUpdate(BOOTSTRAP_LOCK_WORKSPACE_ID);
        if (lockWorkspace == null) {
            throw new IllegalStateException("reserved bootstrap lock workspace does not exist: "
                    + BOOTSTRAP_LOCK_WORKSPACE_ID);
        }
    }

    private void restoreWorkspaceRelation(Long userId, String workspaceId) {
        AuthUserWorkspaceData relationData = userWorkspaceMapper.selectOne(
                Wrappers.<AuthUserWorkspaceData>lambdaQuery()
                        .eq(AuthUserWorkspaceData::getUserId, userId)
                        .eq(AuthUserWorkspaceData::getWorkspaceId, workspaceId)
                        .last("limit 1"));
        if (relationData == null) {
            relationData = new AuthUserWorkspaceData();
            relationData.setId(snowflakeIdGenerator.nextId());
            relationData.setUserId(userId);
            relationData.setWorkspaceId(workspaceId);
            relationData.setStatus(NORMAL_STATUS);
            userWorkspaceMapper.insert(relationData);
            return;
        }
        if (!Integer.valueOf(NORMAL_STATUS).equals(relationData.getStatus())) {
            relationData.setStatus(NORMAL_STATUS);
            userWorkspaceMapper.updateById(relationData);
        }
    }
}
