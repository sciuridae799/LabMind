package com.superagent.business.chat.chatagent.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.superagent.business.chat.chatagent.data.BusinessChatDialogueData;
import com.superagent.business.chat.chatagent.data.BusinessChatSessionStateData;
import com.superagent.business.chat.chatagent.mapper.BusinessChatDialogueMapper;
import com.superagent.business.chat.chatagent.mapper.BusinessChatSessionStateMapper;
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
 * <p>它维护一个全局当前会话游标，不表达会话业务终态；会话是否存在仍以 dialogue 主表为准。</p>
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
    public void activate(String conversationId) {
        String normalizedConversationId = normalizeConversationId(conversationId);
        // CHAT_PAGE 是管理端聊天页的全局游标，只保存当前正在查看或执行的会话编号。
        BusinessChatSessionStateData stateData = loadChatPageState();
        if (stateData == null) {
            stateData = new BusinessChatSessionStateData();
            stateData.setId(snowflakeIdGenerator.nextId());
            stateData.setStateKey(CHAT_PAGE_STATE_KEY);
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
    public void clearIfActive(String conversationId) {
        String normalizedConversationId = normalizeConversationId(conversationId);
        // 只清理仍指向本轮会话的游标，避免旧请求结束时误清掉新激活的会话。
        sessionStateMapper.update(null, Wrappers.<BusinessChatSessionStateData>lambdaUpdate()
                .eq(BusinessChatSessionStateData::getStateKey, CHAT_PAGE_STATE_KEY)
                .eq(BusinessChatSessionStateData::getActiveConversationId, normalizedConversationId)
                .eq(BusinessChatSessionStateData::getStatus, NORMAL_STATUS)
                .set(BusinessChatSessionStateData::getActiveConversationId, null));
    }

    @Override
    @Transactional
    public void clearActive() {
        sessionStateMapper.update(null, Wrappers.<BusinessChatSessionStateData>lambdaUpdate()
                .eq(BusinessChatSessionStateData::getStateKey, CHAT_PAGE_STATE_KEY)
                .eq(BusinessChatSessionStateData::getStatus, NORMAL_STATUS)
                .set(BusinessChatSessionStateData::getActiveConversationId, null));
    }

    @Override
    public String getActiveConversationId() {
        BusinessChatSessionStateData stateData = loadChatPageState();
        if (stateData == null || !StringUtils.hasText(stateData.getActiveConversationId())) {
            return null;
        }

        String conversationId = stateData.getActiveConversationId().strip();
        // 游标必须回表确认会话仍有效，避免返回已被删除或状态失效的历史 conversationId。
        BusinessChatDialogueData dialogueData = dialogueMapper.selectOne(
                Wrappers.<BusinessChatDialogueData>lambdaQuery()
                        .eq(BusinessChatDialogueData::getDialogueCode, conversationId)
                        .eq(BusinessChatDialogueData::getStatus, NORMAL_STATUS)
                        .last("limit 1"));
        return dialogueData == null ? null : conversationId;
    }

    private BusinessChatSessionStateData loadChatPageState() {
        return sessionStateMapper.selectOne(
                Wrappers.<BusinessChatSessionStateData>lambdaQuery()
                        .eq(BusinessChatSessionStateData::getStateKey, CHAT_PAGE_STATE_KEY)
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
}
