package com.superagent.business.chat.chatagent.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.superagent.business.chat.chatagent.persistence.data.BusinessChatDialogueData;
import com.superagent.business.chat.chatagent.persistence.data.BusinessChatSessionStateData;
import com.superagent.business.chat.chatagent.persistence.mapper.BusinessChatDialogueMapper;
import com.superagent.business.chat.chatagent.persistence.mapper.BusinessChatSessionStateMapper;
import com.superagent.business.chat.chatagent.service.BusinessChatSessionStateService;
import com.superagent.common.frame.enums.BaseCode;
import com.superagent.common.frame.exception.BaseException;
import com.superagent.idgenerator.toolkit.SnowflakeIdGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 管理端聊天页状态服务。
 *
 * <p>它按工作组和访客登录会话维护当前会话游标，不表达会话业务终态；会话是否存在仍以 dialogue 主表为准。</p>
 */
@Service
@RequiredArgsConstructor
public class BusinessChatSessionStateServiceImpl implements BusinessChatSessionStateService {

    private static final int NORMAL_STATUS = 1;

    private static final String CHAT_PAGE_STATE_KEY = "CHAT_PAGE";

    private final BusinessChatSessionStateMapper sessionStateMapper;

    private final BusinessChatDialogueMapper dialogueMapper;

    private final SnowflakeIdGenerator snowflakeIdGenerator;

    @Override
    @Transactional
    public void activate(String conversationId, String workspaceId, String authSessionToken) {
        String normalizedConversationId = normalizeConversationId(conversationId);
        String normalizedWorkspaceId = normalizeWorkspaceId(workspaceId);
        String normalizedAuthSessionToken = normalizeAuthSessionToken(authSessionToken);
        BusinessChatSessionStateData stateData = loadChatPageState(normalizedWorkspaceId, normalizedAuthSessionToken);
        if (stateData == null) {
            stateData = new BusinessChatSessionStateData();
            stateData.setId(snowflakeIdGenerator.nextId());
            stateData.setStateKey(CHAT_PAGE_STATE_KEY);
            stateData.setWorkspaceId(normalizedWorkspaceId);
            stateData.setAuthSessionToken(normalizedAuthSessionToken);
            stateData.setActiveConversationId(normalizedConversationId);
            stateData.setStatus(NORMAL_STATUS);
            sessionStateMapper.insert(stateData);
            return;
        }
        stateData.setActiveConversationId(normalizedConversationId);
        stateData.setStatus(NORMAL_STATUS);
        sessionStateMapper.updateById(stateData);
    }

    @Override
    @Transactional
    public void clearIfActive(String conversationId, String workspaceId, String authSessionToken) {
        String normalizedConversationId = normalizeConversationId(conversationId);
        String normalizedWorkspaceId = normalizeWorkspaceId(workspaceId);
        String normalizedAuthSessionToken = normalizeAuthSessionToken(authSessionToken);
        // 只清理仍指向本轮会话的游标，避免旧请求结束时误清掉新激活的会话。
        sessionStateMapper.update(null, Wrappers.<BusinessChatSessionStateData>lambdaUpdate()
                .eq(BusinessChatSessionStateData::getStateKey, CHAT_PAGE_STATE_KEY)
                .eq(BusinessChatSessionStateData::getWorkspaceId, normalizedWorkspaceId)
                .eq(BusinessChatSessionStateData::getAuthSessionToken, normalizedAuthSessionToken)
                .eq(BusinessChatSessionStateData::getActiveConversationId, normalizedConversationId)
                .eq(BusinessChatSessionStateData::getStatus, NORMAL_STATUS)
                .set(BusinessChatSessionStateData::getActiveConversationId, null));
    }

    @Override
    @Transactional
    public void clearActive(String workspaceId, String authSessionToken) {
        String normalizedWorkspaceId = normalizeWorkspaceId(workspaceId);
        String normalizedAuthSessionToken = normalizeAuthSessionToken(authSessionToken);
        sessionStateMapper.update(null, Wrappers.<BusinessChatSessionStateData>lambdaUpdate()
                .eq(BusinessChatSessionStateData::getStateKey, CHAT_PAGE_STATE_KEY)
                .eq(BusinessChatSessionStateData::getWorkspaceId, normalizedWorkspaceId)
                .eq(BusinessChatSessionStateData::getAuthSessionToken, normalizedAuthSessionToken)
                .eq(BusinessChatSessionStateData::getStatus, NORMAL_STATUS)
                .set(BusinessChatSessionStateData::getActiveConversationId, null));
    }

    @Override
    public String getActiveConversationId(String workspaceId, String authSessionToken) {
        String normalizedWorkspaceId = normalizeWorkspaceId(workspaceId);
        String normalizedAuthSessionToken = normalizeAuthSessionToken(authSessionToken);
        BusinessChatSessionStateData stateData = loadChatPageState(normalizedWorkspaceId, normalizedAuthSessionToken);
        if (stateData == null || !StringUtils.hasText(stateData.getActiveConversationId())) {
            return null;
        }

        String conversationId = stateData.getActiveConversationId().strip();
        // 游标必须回表确认会话仍有效，避免返回已被删除或状态失效的历史 conversationId。
        BusinessChatDialogueData dialogueData = dialogueMapper.selectOne(
                Wrappers.<BusinessChatDialogueData>lambdaQuery()
                        .eq(BusinessChatDialogueData::getDialogueCode, conversationId)
                        .eq(BusinessChatDialogueData::getWorkspaceId, normalizedWorkspaceId)
                        .eq(BusinessChatDialogueData::getAuthSessionToken, normalizedAuthSessionToken)
                        .eq(BusinessChatDialogueData::getStatus, NORMAL_STATUS)
                        .last("limit 1"));
        return dialogueData == null ? null : conversationId;
    }

    private BusinessChatSessionStateData loadChatPageState(String workspaceId, String authSessionToken) {
        return sessionStateMapper.selectOne(
                Wrappers.<BusinessChatSessionStateData>lambdaQuery()
                        .eq(BusinessChatSessionStateData::getStateKey, CHAT_PAGE_STATE_KEY)
                        .eq(BusinessChatSessionStateData::getWorkspaceId, workspaceId)
                        .eq(BusinessChatSessionStateData::getAuthSessionToken, authSessionToken)
                        .eq(BusinessChatSessionStateData::getStatus, NORMAL_STATUS)
                        .last("limit 1"));
    }

    private String normalizeConversationId(String conversationId) {
        String normalizedConversationId = conversationId == null ? null : conversationId.strip();
        if (!StringUtils.hasText(normalizedConversationId)) {
            throw new BaseException(BaseCode.INVALID_PARAMETER, "conversationId must not be blank");
        }
        return normalizedConversationId;
    }

    private String normalizeWorkspaceId(String workspaceId) {
        String normalizedWorkspaceId = workspaceId == null ? null : workspaceId.strip();
        if (!StringUtils.hasText(normalizedWorkspaceId)) {
            throw new BaseException(BaseCode.INVALID_PARAMETER, "workspaceId must not be blank");
        }
        return normalizedWorkspaceId;
    }

    private String normalizeAuthSessionToken(String authSessionToken) {
        String normalizedAuthSessionToken = authSessionToken == null ? "" : authSessionToken.strip();
        return StringUtils.hasText(normalizedAuthSessionToken) ? normalizedAuthSessionToken : "";
    }
}
