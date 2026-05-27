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
        long superAdminCount = userAccountMapper.selectCount(Wrappers.<AuthUserAccountData>lambdaQuery()
                .eq(AuthUserAccountData::getRole, AuthRole.SUPER_ADMIN.value())
                .eq(AuthUserAccountData::getStatus, NORMAL_STATUS));
        if (superAdminCount > 0) {
            return;
        }
        AuthWorkspaceData workspaceData = workspaceMapper.selectOne(Wrappers.<AuthWorkspaceData>lambdaQuery()
                .eq(AuthWorkspaceData::getWorkspaceId, properties.getWorkspaceId().strip())
                .eq(AuthWorkspaceData::getStatus, NORMAL_STATUS)
                .last("limit 1"));
        if (workspaceData == null) {
            workspaceData = new AuthWorkspaceData();
            workspaceData.setId(snowflakeIdGenerator.nextId());
            workspaceData.setWorkspaceId(properties.getWorkspaceId().strip());
            workspaceData.setWorkspaceName(properties.getWorkspaceName().strip());
            workspaceData.setStatus(NORMAL_STATUS);
            workspaceMapper.insert(workspaceData);
        }

        AuthUserAccountData existingAccount = userAccountMapper.selectOne(Wrappers.<AuthUserAccountData>lambdaQuery()
                .eq(AuthUserAccountData::getAccount, properties.getSuperAdminAccount().strip())
                .eq(AuthUserAccountData::getStatus, NORMAL_STATUS)
                .last("limit 1"));
        if (existingAccount != null) {
            throw new IllegalStateException(
                    "super-agent.auth.bootstrap.super-admin-account already exists with role "
                            + existingAccount.getRole());
        }

        String salt = passwordHasher.newSalt();
        AuthUserAccountData userData = new AuthUserAccountData();
        userData.setId(snowflakeIdGenerator.nextId());
        userData.setAccount(properties.getSuperAdminAccount().strip());
        userData.setDisplayName(properties.getSuperAdminDisplayName().strip());
        userData.setPasswordSalt(salt);
        userData.setPasswordHash(passwordHasher.hash(properties.getSuperAdminPassword(), salt));
        userData.setRole(AuthRole.SUPER_ADMIN.value());
        userData.setWorkspaceId(workspaceData.getWorkspaceId());
        userData.setEnabled(ENABLED);
        userData.setStatus(NORMAL_STATUS);
        userAccountMapper.insert(userData);

        AuthUserWorkspaceData relationData = new AuthUserWorkspaceData();
        relationData.setId(snowflakeIdGenerator.nextId());
        relationData.setUserId(userData.getId());
        relationData.setWorkspaceId(workspaceData.getWorkspaceId());
        relationData.setStatus(NORMAL_STATUS);
        userWorkspaceMapper.insert(relationData);
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
}
