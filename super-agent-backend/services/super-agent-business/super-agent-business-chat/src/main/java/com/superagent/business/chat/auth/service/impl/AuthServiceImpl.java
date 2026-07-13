package com.superagent.business.chat.auth.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.superagent.business.chat.auth.AuthErrorCode;
import com.superagent.business.chat.auth.AuthException;
import com.superagent.business.chat.auth.AuthRole;
import com.superagent.business.chat.auth.AuthSessionContext;
import com.superagent.business.chat.auth.AuthSessionHolder;
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
import com.superagent.business.chat.auth.data.AuthSessionData;
import com.superagent.business.chat.auth.data.AuthUserAccountData;
import com.superagent.business.chat.auth.data.AuthUserWorkspaceData;
import com.superagent.business.chat.auth.data.AuthWorkspaceData;
import com.superagent.business.chat.auth.mapper.AuthSessionMapper;
import com.superagent.business.chat.auth.mapper.AuthUserAccountMapper;
import com.superagent.business.chat.auth.mapper.AuthUserWorkspaceMapper;
import com.superagent.business.chat.auth.mapper.AuthWorkspaceMapper;
import com.superagent.business.chat.auth.service.AuthService;
import com.superagent.business.chat.auth.service.AuthWorkspaceScopeService;
import com.superagent.business.chat.auth.support.AuthPasswordHasher;
import com.superagent.business.chat.chatagent.persistence.data.BusinessChatDialogueData;
import com.superagent.business.chat.chatagent.persistence.mapper.BusinessChatDialogueMapper;
import com.superagent.business.chat.knowledge.document.data.KnowledgeDocumentData;
import com.superagent.business.chat.knowledge.document.mapper.KnowledgeDocumentMapper;
import com.superagent.business.chat.support.BusinessInputValidator;
import com.superagent.common.frame.enums.BaseCode;
import com.superagent.common.frame.exception.BaseException;
import com.superagent.idgenerator.toolkit.SnowflakeIdGenerator;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final int NORMAL_STATUS = 1;
    private static final int DELETED_STATUS = 0;
    private static final int ENABLED = 1;
    private static final String GUEST_WORKSPACE_ID = "public-demo";
    private static final long SESSION_TTL_HOURS = 24L;

    private final AuthUserAccountMapper userAccountMapper;
    private final AuthUserWorkspaceMapper userWorkspaceMapper;
    private final AuthWorkspaceMapper workspaceMapper;
    private final AuthSessionMapper sessionMapper;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final BusinessChatDialogueMapper businessChatDialogueMapper;
    private final AuthWorkspaceScopeService workspaceScopeService;
    private final AuthPasswordHasher passwordHasher;
    private final SnowflakeIdGenerator snowflakeIdGenerator;

    @Override
    @Transactional
    public AuthSessionVo login(AuthLoginRequest request) {
        String account = BusinessInputValidator.normalizeRequiredText(request.getAccount(), "account");
        String password = BusinessInputValidator.normalizeRequiredText(request.getPassword(), "password");
        AuthUserAccountData userData = userAccountMapper.selectOne(
                Wrappers.<AuthUserAccountData>lambdaQuery()
                        .eq(AuthUserAccountData::getAccount, account)
                        .eq(AuthUserAccountData::getStatus, NORMAL_STATUS)
                        .last("limit 1"));
        if (userData == null || !Integer.valueOf(ENABLED).equals(userData.getEnabled())) {
            throw new AuthException(AuthErrorCode.AUTH_REQUIRED, "账号不存在或已停用");
        }
        if (!passwordHasher.matches(password, userData.getPasswordSalt(), userData.getPasswordHash())) {
            throw new AuthException(AuthErrorCode.AUTH_REQUIRED, "账号或密码不正确");
        }
        List<AuthWorkspaceData> accessibleWorkspaces = loadAccessibleWorkspaces(userData);
        AuthWorkspaceData workspaceData = selectCurrentWorkspace(userData.getWorkspaceId(), accessibleWorkspaces);
        return toSessionVo(createSession(userData, workspaceData), userData, workspaceData, accessibleWorkspaces);
    }

    @Override
    @Transactional
    public AuthSessionVo loginGuest() {
        AuthWorkspaceData workspaceData = loadNormalWorkspace(GUEST_WORKSPACE_ID);
        AuthSessionData sessionData = new AuthSessionData();
        sessionData.setId(snowflakeIdGenerator.nextId());
        sessionData.setToken(newToken());
        sessionData.setAccount("guest");
        sessionData.setDisplayName("访客");
        sessionData.setRole(AuthRole.GUEST.value());
        sessionData.setWorkspaceId(workspaceData.getWorkspaceId());
        sessionData.setExpireTime(LocalDateTime.now().plusHours(SESSION_TTL_HOURS));
        sessionData.setStatus(NORMAL_STATUS);
        sessionMapper.insert(sessionData);
        AuthSessionVo vo = new AuthSessionVo();
        vo.setToken(sessionData.getToken());
        vo.setUserId("guest");
        vo.setAccount(sessionData.getAccount());
        vo.setDisplayName(sessionData.getDisplayName());
        vo.setRole(sessionData.getRole());
        vo.setWorkspaceId(workspaceData.getWorkspaceId());
        vo.setWorkspaceName(workspaceData.getWorkspaceName());
        vo.setAccessibleWorkspaces(List.of(toWorkspaceVo(workspaceData)));
        return vo;
    }

    @Override
    public AuthSessionContext loadSession(String token) {
        String normalizedToken = BusinessInputValidator.normalizeRequiredText(token, "token");
        AuthSessionData sessionData = sessionMapper.selectOne(
                Wrappers.<AuthSessionData>lambdaQuery()
                        .eq(AuthSessionData::getToken, normalizedToken)
                        .eq(AuthSessionData::getStatus, NORMAL_STATUS)
                        .last("limit 1"));
        if (sessionData == null || sessionData.getExpireTime() == null
                || !sessionData.getExpireTime().isAfter(LocalDateTime.now())) {
            throw new AuthException(AuthErrorCode.AUTH_REQUIRED, "登录状态已失效，请重新登录");
        }
        AuthWorkspaceData workspaceData = loadNormalWorkspace(sessionData.getWorkspaceId());
        return new AuthSessionContext(
                sessionData.getToken(),
                sessionData.getUserId() == null ? "guest" : String.valueOf(sessionData.getUserId()),
                sessionData.getAccount(),
                sessionData.getDisplayName(),
                AuthRole.fromValue(sessionData.getRole()),
                workspaceData.getWorkspaceId(),
                workspaceData.getWorkspaceName());
    }

    @Override
    public AuthSessionVo currentSession() {
        return toSessionVo(AuthSessionHolder.required());
    }

    @Override
    @Transactional
    public AuthSessionVo switchWorkspace(AuthSwitchWorkspaceRequest request) {
        AuthSessionContext session = AuthSessionHolder.required();
        String workspaceId = BusinessInputValidator.normalizeRequiredText(request.getWorkspaceId(), "workspaceId");
        AuthWorkspaceData workspaceData = loadNormalWorkspace(workspaceId);
        List<AuthWorkspaceData> accessibleWorkspaces = loadAccessibleWorkspaces(session);
        if (accessibleWorkspaces.stream().noneMatch(workspace -> workspace.getWorkspaceId().equals(workspaceData.getWorkspaceId()))) {
            throw new AuthException(AuthErrorCode.AUTH_FORBIDDEN, "当前账号不能切换到该工作组");
        }
        sessionMapper.update(null, Wrappers.<AuthSessionData>lambdaUpdate()
                .eq(AuthSessionData::getToken, session.token())
                .eq(AuthSessionData::getStatus, NORMAL_STATUS)
                .set(AuthSessionData::getWorkspaceId, workspaceData.getWorkspaceId()));
        AuthSessionContext switchedContext = new AuthSessionContext(
                session.token(),
                session.userId(),
                session.account(),
                session.displayName(),
                session.role(),
                workspaceData.getWorkspaceId(),
                workspaceData.getWorkspaceName());
        return toSessionVo(switchedContext, accessibleWorkspaces);
    }

    @Override
    @Transactional
    public void logout() {
        AuthSessionContext session = AuthSessionHolder.required();
        sessionMapper.update(null, Wrappers.<AuthSessionData>lambdaUpdate()
                .eq(AuthSessionData::getToken, session.token())
                .eq(AuthSessionData::getStatus, NORMAL_STATUS)
                .set(AuthSessionData::getStatus, DELETED_STATUS));
    }

    @Override
    public List<AuthWorkspaceVo> listWorkspaces() {
        workspaceScopeService.requireSuperAdmin();
        return workspaceMapper.selectList(Wrappers.<AuthWorkspaceData>lambdaQuery()
                        .eq(AuthWorkspaceData::getStatus, NORMAL_STATUS)
                        .orderByAsc(AuthWorkspaceData::getWorkspaceId))
                .stream()
                .map(this::toWorkspaceVo)
                .toList();
    }

    @Override
    @Transactional
    public AuthWorkspaceVo createWorkspace(AuthCreateWorkspaceRequest request) {
        workspaceScopeService.requireSuperAdmin();
        String workspaceName = BusinessInputValidator.normalizeRequiredText(request.getWorkspaceName(), "workspaceName");
        String workspaceId = normalizeWorkspaceId(request.getWorkspaceCode(), workspaceName);
        AuthWorkspaceData existing = workspaceMapper.selectOne(
                Wrappers.<AuthWorkspaceData>lambdaQuery()
                        .eq(AuthWorkspaceData::getWorkspaceId, workspaceId)
                        .eq(AuthWorkspaceData::getStatus, NORMAL_STATUS)
                        .last("limit 1"));
        if (existing != null) {
            throw new BaseException(BaseCode.INVALID_PARAMETER, "workspaceId already exists: " + workspaceId);
        }
        AuthWorkspaceData data = new AuthWorkspaceData();
        data.setId(snowflakeIdGenerator.nextId());
        data.setWorkspaceId(workspaceId);
        data.setWorkspaceName(workspaceName);
        data.setStatus(NORMAL_STATUS);
        workspaceMapper.insert(data);
        return toWorkspaceVo(data);
    }

    @Override
    @Transactional
    public AuthWorkspaceVo updateWorkspace(AuthUpdateWorkspaceRequest request) {
        workspaceScopeService.requireSuperAdmin();
        String workspaceId = BusinessInputValidator.normalizeRequiredText(request.getWorkspaceId(), "workspaceId");
        String workspaceName = BusinessInputValidator.normalizeRequiredText(request.getWorkspaceName(), "workspaceName");
        AuthWorkspaceData workspaceData = loadNormalWorkspace(workspaceId);
        workspaceMapper.update(null, Wrappers.<AuthWorkspaceData>lambdaUpdate()
                .eq(AuthWorkspaceData::getWorkspaceId, workspaceData.getWorkspaceId())
                .eq(AuthWorkspaceData::getStatus, NORMAL_STATUS)
                .set(AuthWorkspaceData::getWorkspaceName, workspaceName));
        workspaceData.setWorkspaceName(workspaceName);
        return toWorkspaceVo(workspaceData);
    }

    @Override
    @Transactional
    public void deleteWorkspace(AuthDeleteWorkspaceRequest request) {
        workspaceScopeService.requireSuperAdmin();
        String workspaceId = BusinessInputValidator.normalizeRequiredText(request.getWorkspaceId(), "workspaceId");
        AuthWorkspaceData workspaceData = loadNormalWorkspace(workspaceId);
        if (GUEST_WORKSPACE_ID.equals(workspaceData.getWorkspaceId())) {
            throw new BaseException(
                    BaseCode.INVALID_PARAMETER,
                    "reserved guest workspace cannot be deleted: " + workspaceData.getWorkspaceId());
        }
        requireWorkspaceEmpty(workspaceData.getWorkspaceId());
        workspaceMapper.update(null, Wrappers.<AuthWorkspaceData>lambdaUpdate()
                .eq(AuthWorkspaceData::getWorkspaceId, workspaceData.getWorkspaceId())
                .eq(AuthWorkspaceData::getStatus, NORMAL_STATUS)
                .set(AuthWorkspaceData::getStatus, DELETED_STATUS));
    }

    @Override
    public List<AuthUserAccountVo> listUsers() {
        workspaceScopeService.requireSuperAdmin();
        List<AuthWorkspaceData> workspaces = workspaceMapper.selectList(Wrappers.<AuthWorkspaceData>lambdaQuery()
                .eq(AuthWorkspaceData::getStatus, NORMAL_STATUS));
        Map<String, AuthWorkspaceData> workspaceById = workspaces.stream()
                .collect(Collectors.toMap(AuthWorkspaceData::getWorkspaceId, Function.identity()));
        List<AuthUserAccountData> users = userAccountMapper.selectList(Wrappers.<AuthUserAccountData>lambdaQuery()
                        .eq(AuthUserAccountData::getStatus, NORMAL_STATUS)
                        .orderByDesc(AuthUserAccountData::getCreateTime)
                        .orderByDesc(AuthUserAccountData::getId));
        Map<Long, List<AuthWorkspaceData>> workspacesByUserId = loadWorkspacesByUserId(users, workspaceById);
        return users.stream()
                .map(userData -> toUserVo(
                        userData,
                        workspaceById.get(userData.getWorkspaceId()),
                        workspacesByUserId.getOrDefault(userData.getId(), List.of())))
                .toList();
    }

    @Override
    @Transactional
    public AuthUserAccountVo createUser(AuthCreateUserRequest request) {
        workspaceScopeService.requireSuperAdmin();
        String account = BusinessInputValidator.normalizeRequiredText(request.getAccount(), "account");
        String displayName = BusinessInputValidator.normalizeRequiredText(request.getDisplayName(), "displayName");
        String password = BusinessInputValidator.normalizeRequiredText(request.getPassword(), "password");
        AuthRole role = AuthRole.fromValue(request.getRole());
        if (role == AuthRole.GUEST) {
            throw new BaseException(BaseCode.INVALID_PARAMETER, "guest account is not a managed user role");
        }
        List<String> workspaceIds = normalizeWorkspaceIds(request.getWorkspaceIds());
        List<AuthWorkspaceData> workspaceDataList = loadNormalWorkspaces(workspaceIds);
        AuthWorkspaceData workspaceData = workspaceDataList.get(0);
        AuthUserAccountData existing = userAccountMapper.selectOne(
                Wrappers.<AuthUserAccountData>lambdaQuery()
                        .eq(AuthUserAccountData::getAccount, account)
                        .eq(AuthUserAccountData::getStatus, NORMAL_STATUS)
                        .last("limit 1"));
        if (existing != null) {
            throw new BaseException(BaseCode.INVALID_PARAMETER, "account already exists: " + account);
        }
        String salt = passwordHasher.newSalt();
        AuthUserAccountData userData = new AuthUserAccountData();
        userData.setId(snowflakeIdGenerator.nextId());
        userData.setAccount(account);
        userData.setDisplayName(displayName);
        userData.setPasswordSalt(salt);
        userData.setPasswordHash(passwordHasher.hash(password, salt));
        userData.setRole(role.value());
        userData.setWorkspaceId(workspaceData.getWorkspaceId());
        userData.setEnabled(ENABLED);
        userData.setStatus(NORMAL_STATUS);
        userAccountMapper.insert(userData);
        insertUserWorkspaceRelations(userData.getId(), workspaceDataList);
        return toUserVo(userData, workspaceData, workspaceDataList);
    }

    @Override
    @Transactional
    public AuthUserAccountVo updateUser(AuthUpdateUserRequest request) {
        workspaceScopeService.requireSuperAdmin();
        long userId = BusinessInputValidator.parsePositiveLong(request.getUserId(), "userId");
        String displayName = BusinessInputValidator.normalizeRequiredText(request.getDisplayName(), "displayName");
        AuthRole role = AuthRole.fromValue(request.getRole());
        if (role == AuthRole.GUEST) {
            throw new BaseException(BaseCode.INVALID_PARAMETER, "guest account is not a managed user role");
        }
        List<String> workspaceIds = normalizeWorkspaceIds(request.getWorkspaceIds());
        List<AuthWorkspaceData> workspaceDataList = loadNormalWorkspaces(workspaceIds);
        AuthWorkspaceData workspaceData = workspaceDataList.get(0);
        AuthUserAccountData userData = loadNormalUser(userId);
        int enabled = Boolean.TRUE.equals(request.getEnabled()) ? ENABLED : DELETED_STATUS;
        requireAnotherAvailableSuperAdmin(userData, role, enabled);
        userAccountMapper.update(null, Wrappers.<AuthUserAccountData>lambdaUpdate()
                .eq(AuthUserAccountData::getId, userData.getId())
                .eq(AuthUserAccountData::getStatus, NORMAL_STATUS)
                .set(AuthUserAccountData::getDisplayName, displayName)
                .set(AuthUserAccountData::getRole, role.value())
                .set(AuthUserAccountData::getWorkspaceId, workspaceData.getWorkspaceId())
                .set(AuthUserAccountData::getEnabled, enabled));
        replaceUserWorkspaceRelations(userData.getId(), workspaceDataList);
        invalidateUserSessions(userData.getId());
        userData.setDisplayName(displayName);
        userData.setRole(role.value());
        userData.setWorkspaceId(workspaceData.getWorkspaceId());
        userData.setEnabled(enabled);
        return toUserVo(userData, workspaceData, workspaceDataList);
    }

    @Override
    @Transactional
    public void deleteUser(AuthDeleteUserRequest request) {
        workspaceScopeService.requireSuperAdmin();
        long userId = BusinessInputValidator.parsePositiveLong(request.getUserId(), "userId");
        AuthUserAccountData userData = loadNormalUser(userId);
        requireAnotherAvailableSuperAdmin(userData, AuthRole.USER, DELETED_STATUS);
        userAccountMapper.update(null, Wrappers.<AuthUserAccountData>lambdaUpdate()
                .eq(AuthUserAccountData::getId, userData.getId())
                .eq(AuthUserAccountData::getStatus, NORMAL_STATUS)
                .set(AuthUserAccountData::getStatus, DELETED_STATUS)
                .set(AuthUserAccountData::getEnabled, DELETED_STATUS));
        userWorkspaceMapper.update(null, Wrappers.<AuthUserWorkspaceData>lambdaUpdate()
                .eq(AuthUserWorkspaceData::getUserId, userData.getId())
                .eq(AuthUserWorkspaceData::getStatus, NORMAL_STATUS)
                .set(AuthUserWorkspaceData::getStatus, DELETED_STATUS));
        invalidateUserSessions(userData.getId());
    }

    private AuthSessionData createSession(AuthUserAccountData userData, AuthWorkspaceData workspaceData) {
        AuthSessionData sessionData = new AuthSessionData();
        sessionData.setId(snowflakeIdGenerator.nextId());
        sessionData.setToken(newToken());
        sessionData.setUserId(userData.getId());
        sessionData.setAccount(userData.getAccount());
        sessionData.setDisplayName(userData.getDisplayName());
        sessionData.setRole(userData.getRole());
        sessionData.setWorkspaceId(workspaceData.getWorkspaceId());
        sessionData.setExpireTime(LocalDateTime.now().plusHours(SESSION_TTL_HOURS));
        sessionData.setStatus(NORMAL_STATUS);
        sessionMapper.insert(sessionData);
        return sessionData;
    }

    private AuthWorkspaceData loadNormalWorkspace(String workspaceId) {
        String normalizedWorkspaceId = BusinessInputValidator.normalizeRequiredText(workspaceId, "workspaceId");
        AuthWorkspaceData workspaceData = workspaceMapper.selectOne(
                Wrappers.<AuthWorkspaceData>lambdaQuery()
                        .eq(AuthWorkspaceData::getWorkspaceId, normalizedWorkspaceId)
                        .eq(AuthWorkspaceData::getStatus, NORMAL_STATUS)
                        .last("limit 1"));
        if (workspaceData == null) {
            throw new AuthException(AuthErrorCode.AUTH_FORBIDDEN, "工作组不存在或不可用：" + normalizedWorkspaceId);
        }
        return workspaceData;
    }

    private AuthUserAccountData loadNormalUser(long userId) {
        AuthUserAccountData userData = userAccountMapper.selectOne(
                Wrappers.<AuthUserAccountData>lambdaQuery()
                        .eq(AuthUserAccountData::getId, userId)
                        .eq(AuthUserAccountData::getStatus, NORMAL_STATUS)
                        .last("limit 1"));
        if (userData == null) {
            throw new BaseException(BaseCode.INVALID_PARAMETER, "user does not exist: " + userId);
        }
        return userData;
    }

    private void requireAnotherAvailableSuperAdmin(
            AuthUserAccountData currentUser,
            AuthRole requestedRole,
            int requestedEnabled) {
        if (!isAvailableSuperAdmin(currentUser)
                || isAvailableSuperAdmin(requestedRole, requestedEnabled, NORMAL_STATUS)) {
            return;
        }
        List<Long> lockedSuperAdminIds = userAccountMapper.selectAvailableSuperAdminIdsForUpdate(
                AuthRole.SUPER_ADMIN.value(), ENABLED, NORMAL_STATUS);
        boolean hasAnotherAvailableSuperAdmin = lockedSuperAdminIds.stream()
                .anyMatch(userId -> !currentUser.getId().equals(userId));
        if (!hasAnotherAvailableSuperAdmin) {
            throw new BaseException(
                    BaseCode.INVALID_PARAMETER,
                    "at least one enabled super_admin account is required");
        }
    }

    private boolean isAvailableSuperAdmin(AuthUserAccountData userData) {
        return isAvailableSuperAdmin(
                AuthRole.fromValue(userData.getRole()),
                userData.getEnabled(),
                userData.getStatus());
    }

    private boolean isAvailableSuperAdmin(AuthRole role, Integer enabled, Integer status) {
        return role == AuthRole.SUPER_ADMIN
                && Integer.valueOf(ENABLED).equals(enabled)
                && Integer.valueOf(NORMAL_STATUS).equals(status);
    }

    private void requireWorkspaceEmpty(String workspaceId) {
        Long defaultUserCount = userAccountMapper.selectCount(Wrappers.<AuthUserAccountData>lambdaQuery()
                .eq(AuthUserAccountData::getWorkspaceId, workspaceId)
                .eq(AuthUserAccountData::getStatus, NORMAL_STATUS));
        Long relationCount = userWorkspaceMapper.selectCount(Wrappers.<AuthUserWorkspaceData>lambdaQuery()
                .eq(AuthUserWorkspaceData::getWorkspaceId, workspaceId)
                .eq(AuthUserWorkspaceData::getStatus, NORMAL_STATUS));
        if (defaultUserCount > 0 || relationCount > 0) {
            throw new BaseException(BaseCode.INVALID_PARAMETER, "workspace has user accounts: " + workspaceId);
        }
        Long documentCount = knowledgeDocumentMapper.selectCount(Wrappers.<KnowledgeDocumentData>lambdaQuery()
                .eq(KnowledgeDocumentData::getWorkspaceId, workspaceId)
                .eq(KnowledgeDocumentData::getStatus, NORMAL_STATUS));
        if (documentCount > 0) {
            throw new BaseException(BaseCode.INVALID_PARAMETER, "workspace has documents: " + workspaceId);
        }
        Long dialogueCount = businessChatDialogueMapper.selectCount(Wrappers.<BusinessChatDialogueData>lambdaQuery()
                .eq(BusinessChatDialogueData::getWorkspaceId, workspaceId)
                .eq(BusinessChatDialogueData::getStatus, NORMAL_STATUS));
        if (dialogueCount > 0) {
            throw new BaseException(BaseCode.INVALID_PARAMETER, "workspace has chat sessions: " + workspaceId);
        }
    }

    private List<AuthWorkspaceData> loadNormalWorkspaces(List<String> workspaceIds) {
        List<AuthWorkspaceData> workspaceDataList = workspaceMapper.selectList(
                Wrappers.<AuthWorkspaceData>lambdaQuery()
                        .in(AuthWorkspaceData::getWorkspaceId, workspaceIds)
                        .eq(AuthWorkspaceData::getStatus, NORMAL_STATUS));
        Map<String, AuthWorkspaceData> workspaceById = workspaceDataList.stream()
                .collect(Collectors.toMap(AuthWorkspaceData::getWorkspaceId, Function.identity()));
        return workspaceIds.stream()
                .map(workspaceId -> {
                    AuthWorkspaceData workspaceData = workspaceById.get(workspaceId);
                    if (workspaceData == null) {
                        throw new AuthException(AuthErrorCode.AUTH_FORBIDDEN, "工作组不存在或不可用：" + workspaceId);
                    }
                    return workspaceData;
                })
                .toList();
    }

    private List<AuthWorkspaceData> loadAccessibleWorkspaces(AuthUserAccountData userData) {
        if (AuthRole.fromValue(userData.getRole()) == AuthRole.SUPER_ADMIN) {
            return loadAllNormalWorkspaces();
        }
        return loadUserWorkspaces(userData.getId());
    }

    private List<AuthWorkspaceData> loadAccessibleWorkspaces(AuthSessionContext session) {
        if (session.role() == AuthRole.GUEST) {
            return List.of(loadNormalWorkspace(session.workspaceId()));
        }
        if (session.isSuperAdmin()) {
            return loadAllNormalWorkspaces();
        }
        return loadUserWorkspaces(Long.valueOf(session.userId()));
    }

    private List<AuthWorkspaceData> loadAllNormalWorkspaces() {
        List<AuthWorkspaceData> workspaces = workspaceMapper.selectList(Wrappers.<AuthWorkspaceData>lambdaQuery()
                .eq(AuthWorkspaceData::getStatus, NORMAL_STATUS)
                .orderByAsc(AuthWorkspaceData::getWorkspaceId));
        if (workspaces.isEmpty()) {
            throw new AuthException(AuthErrorCode.AUTH_FORBIDDEN, "没有可用工作组");
        }
        return workspaces;
    }

    private List<AuthWorkspaceData> loadUserWorkspaces(Long userId) {
        List<AuthUserWorkspaceData> relations = userWorkspaceMapper.selectList(
                Wrappers.<AuthUserWorkspaceData>lambdaQuery()
                        .eq(AuthUserWorkspaceData::getUserId, userId)
                        .eq(AuthUserWorkspaceData::getStatus, NORMAL_STATUS)
                        .orderByAsc(AuthUserWorkspaceData::getWorkspaceId));
        if (relations.isEmpty()) {
            throw new AuthException(AuthErrorCode.AUTH_FORBIDDEN, "当前账号没有可访问工作组");
        }
        return loadNormalWorkspaces(relations.stream()
                .map(AuthUserWorkspaceData::getWorkspaceId)
                .toList());
    }

    private Map<Long, List<AuthWorkspaceData>> loadWorkspacesByUserId(
            List<AuthUserAccountData> users,
            Map<String, AuthWorkspaceData> workspaceById) {
        List<Long> userIds = users.stream()
                .map(AuthUserAccountData::getId)
                .toList();
        if (userIds.isEmpty()) {
            return Map.of();
        }
        List<AuthUserWorkspaceData> relations = userWorkspaceMapper.selectList(
                Wrappers.<AuthUserWorkspaceData>lambdaQuery()
                        .in(AuthUserWorkspaceData::getUserId, userIds)
                        .eq(AuthUserWorkspaceData::getStatus, NORMAL_STATUS)
                        .orderByAsc(AuthUserWorkspaceData::getWorkspaceId));
        return relations.stream()
                .filter(relation -> workspaceById.containsKey(relation.getWorkspaceId()))
                .collect(Collectors.groupingBy(
                        AuthUserWorkspaceData::getUserId,
                        Collectors.mapping(relation -> workspaceById.get(relation.getWorkspaceId()), Collectors.toList())));
    }

    private AuthWorkspaceData selectCurrentWorkspace(String workspaceId, List<AuthWorkspaceData> accessibleWorkspaces) {
        return accessibleWorkspaces.stream()
                .filter(workspace -> workspace.getWorkspaceId().equals(workspaceId))
                .findFirst()
                .orElseThrow(() -> new AuthException(AuthErrorCode.AUTH_FORBIDDEN, "当前账号默认工作组不可用"));
    }

    private List<String> normalizeWorkspaceIds(List<String> workspaceIds) {
        if (workspaceIds == null || workspaceIds.isEmpty()) {
            throw new BaseException(BaseCode.INVALID_PARAMETER, "workspaceIds must not be empty");
        }
        Set<String> normalizedIds = new LinkedHashSet<>();
        for (String workspaceId : workspaceIds) {
            String normalizedWorkspaceId = BusinessInputValidator.normalizeRequiredText(workspaceId, "workspaceId");
            normalizedIds.add(normalizedWorkspaceId);
        }
        return List.copyOf(normalizedIds);
    }

    private void insertUserWorkspaceRelations(Long userId, List<AuthWorkspaceData> workspaceDataList) {
        for (AuthWorkspaceData workspaceData : workspaceDataList) {
            AuthUserWorkspaceData relationData = new AuthUserWorkspaceData();
            relationData.setId(snowflakeIdGenerator.nextId());
            relationData.setUserId(userId);
            relationData.setWorkspaceId(workspaceData.getWorkspaceId());
            relationData.setStatus(NORMAL_STATUS);
            userWorkspaceMapper.insert(relationData);
        }
    }

    private void replaceUserWorkspaceRelations(Long userId, List<AuthWorkspaceData> workspaceDataList) {
        Set<String> selectedWorkspaceIds = workspaceDataList.stream()
                .map(AuthWorkspaceData::getWorkspaceId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<AuthUserWorkspaceData> existingRelations = userWorkspaceMapper.selectList(
                Wrappers.<AuthUserWorkspaceData>lambdaQuery()
                        .eq(AuthUserWorkspaceData::getUserId, userId));
        Set<String> existingWorkspaceIds = existingRelations.stream()
                .map(AuthUserWorkspaceData::getWorkspaceId)
                .collect(Collectors.toSet());
        for (String workspaceId : selectedWorkspaceIds) {
            if (existingWorkspaceIds.contains(workspaceId)) {
                userWorkspaceMapper.update(null, Wrappers.<AuthUserWorkspaceData>lambdaUpdate()
                        .eq(AuthUserWorkspaceData::getUserId, userId)
                        .eq(AuthUserWorkspaceData::getWorkspaceId, workspaceId)
                        .set(AuthUserWorkspaceData::getStatus, NORMAL_STATUS));
            } else {
                AuthUserWorkspaceData relationData = new AuthUserWorkspaceData();
                relationData.setId(snowflakeIdGenerator.nextId());
                relationData.setUserId(userId);
                relationData.setWorkspaceId(workspaceId);
                relationData.setStatus(NORMAL_STATUS);
                userWorkspaceMapper.insert(relationData);
            }
        }
        for (AuthUserWorkspaceData relationData : existingRelations) {
            if (!selectedWorkspaceIds.contains(relationData.getWorkspaceId())
                    && Integer.valueOf(NORMAL_STATUS).equals(relationData.getStatus())) {
                userWorkspaceMapper.update(null, Wrappers.<AuthUserWorkspaceData>lambdaUpdate()
                        .eq(AuthUserWorkspaceData::getId, relationData.getId())
                        .eq(AuthUserWorkspaceData::getStatus, NORMAL_STATUS)
                        .set(AuthUserWorkspaceData::getStatus, DELETED_STATUS));
            }
        }
    }

    private void invalidateUserSessions(Long userId) {
        sessionMapper.update(null, Wrappers.<AuthSessionData>lambdaUpdate()
                .eq(AuthSessionData::getUserId, userId)
                .eq(AuthSessionData::getStatus, NORMAL_STATUS)
                .set(AuthSessionData::getStatus, DELETED_STATUS));
    }

    private String normalizeWorkspaceId(String workspaceCode, String workspaceName) {
        String normalizedCode = workspaceCode == null ? null : workspaceCode.strip();
        if (StringUtils.hasText(normalizedCode)) {
            return normalizedCode;
        }
        return "workspace-" + snowflakeIdGenerator.nextId();
    }

    private String newToken() {
        return UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
    }

    private AuthSessionVo toSessionVo(AuthSessionContext context) {
        return toSessionVo(context, loadAccessibleWorkspaces(context));
    }

    private AuthSessionVo toSessionVo(AuthSessionContext context, List<AuthWorkspaceData> accessibleWorkspaces) {
        AuthSessionVo vo = new AuthSessionVo();
        vo.setToken(context.token());
        vo.setUserId(context.userId());
        vo.setAccount(context.account());
        vo.setDisplayName(context.displayName());
        vo.setRole(context.role().value());
        vo.setWorkspaceId(context.workspaceId());
        vo.setWorkspaceName(context.workspaceName());
        vo.setAccessibleWorkspaces(accessibleWorkspaces.stream().map(this::toWorkspaceVo).toList());
        return vo;
    }

    private AuthSessionVo toSessionVo(
            AuthSessionData sessionData,
            AuthUserAccountData userData,
            AuthWorkspaceData workspaceData,
            List<AuthWorkspaceData> accessibleWorkspaces) {
        AuthSessionVo vo = new AuthSessionVo();
        vo.setToken(sessionData.getToken());
        vo.setUserId(String.valueOf(userData.getId()));
        vo.setAccount(userData.getAccount());
        vo.setDisplayName(userData.getDisplayName());
        vo.setRole(userData.getRole());
        vo.setWorkspaceId(workspaceData.getWorkspaceId());
        vo.setWorkspaceName(workspaceData.getWorkspaceName());
        vo.setAccessibleWorkspaces(accessibleWorkspaces.stream().map(this::toWorkspaceVo).toList());
        return vo;
    }

    private AuthWorkspaceVo toWorkspaceVo(AuthWorkspaceData data) {
        AuthWorkspaceVo vo = new AuthWorkspaceVo();
        vo.setWorkspaceId(data.getWorkspaceId());
        vo.setWorkspaceName(data.getWorkspaceName());
        return vo;
    }

    private AuthUserAccountVo toUserVo(
            AuthUserAccountData userData,
            AuthWorkspaceData workspaceData,
            List<AuthWorkspaceData> workspaceDataList) {
        AuthUserAccountVo vo = new AuthUserAccountVo();
        vo.setUserId(String.valueOf(userData.getId()));
        vo.setAccount(userData.getAccount());
        vo.setDisplayName(userData.getDisplayName());
        vo.setRole(userData.getRole());
        vo.setWorkspaceId(userData.getWorkspaceId());
        vo.setWorkspaceName(workspaceData == null ? "" : workspaceData.getWorkspaceName());
        vo.setWorkspaces(workspaceDataList.stream().map(this::toWorkspaceVo).toList());
        vo.setEnabled(Integer.valueOf(ENABLED).equals(userData.getEnabled()));
        vo.setCreateTime(userData.getCreateTime());
        return vo;
    }
}
