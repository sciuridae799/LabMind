package com.superagent.business.chat.chatagent.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.superagent.business.chat.chatagent.persistence.data.BusinessChatDialogueData;
import com.superagent.business.chat.chatagent.persistence.data.BusinessChatExchangeData;
import com.superagent.business.chat.chatagent.persistence.data.BusinessChatExchangeTraceStageData;
import com.superagent.business.chat.chatagent.persistence.data.BusinessChatMemorySummaryData;
import com.superagent.business.chat.chatagent.api.dto.BusinessChatDeleteSessionRequest;
import com.superagent.business.chat.chatagent.persistence.mapper.BusinessChatDialogueMapper;
import com.superagent.business.chat.chatagent.persistence.mapper.BusinessChatExchangeMapper;
import com.superagent.business.chat.chatagent.persistence.mapper.BusinessChatExchangeTraceStageMapper;
import com.superagent.business.chat.chatagent.persistence.mapper.BusinessChatMemorySummaryMapper;
import com.superagent.business.chat.chatagent.runtime.BusinessChatConversationLeaseKeys;
import com.superagent.business.chat.chatagent.service.BusinessChatErrorCode;
import com.superagent.business.chat.chatagent.service.BusinessChatSessionService;
import com.superagent.business.chat.chatagent.service.BusinessChatSessionStateService;
import com.superagent.business.chat.support.BusinessInputValidator;
import com.superagent.common.frame.exception.BaseException;
import com.superagent.redisson.servicelease.lease.RedisLeaseManager;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 会话生命周期管理服务。
 *
 * <p>当前只承载会话删除：删除前获取同一把会话租约，删除时按 conversationId 统一软删主表、轮次、摘要和 trace。</p>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BusinessChatSessionServiceImpl implements BusinessChatSessionService {

    private static final int NORMAL_STATUS = 1;

    private static final int DELETED_STATUS = 0;

    private static final Duration DELETE_LEASE_TTL = Duration.ofSeconds(30);

    private final RedisLeaseManager redisLeaseManager;

    private final BusinessChatDialogueMapper businessChatDialogueMapper;

    private final BusinessChatExchangeMapper businessChatExchangeMapper;

    private final BusinessChatMemorySummaryMapper businessChatMemorySummaryMapper;

    private final BusinessChatExchangeTraceStageMapper businessChatExchangeTraceStageMapper;

    private final BusinessChatSessionStateService businessChatSessionStateService;

    @Override
    @Transactional
    public void deleteSession(BusinessChatDeleteSessionRequest request) {
        String conversationId = normalizeConversationId(request.getConversationId());
        String leaseKey = BusinessChatConversationLeaseKeys.conversationLeaseKey(conversationId);
        String ownerToken = UUID.randomUUID().toString();
        // 删除和流式生成共用同一把会话锁，保证不会一边写 RUNNING exchange，一边把会话归档软删。
        boolean acquired = redisLeaseManager.acquire(leaseKey, ownerToken, DELETE_LEASE_TTL);
        if (!acquired) {
            throw new BaseException(
                    BusinessChatErrorCode.CHAT_SESSION_RUNNING,
                    "conversation is running and cannot be deleted: " + conversationId);
        }
        try {
            BusinessChatDialogueData dialogueData = businessChatDialogueMapper.selectOne(
                    Wrappers.<BusinessChatDialogueData>lambdaQuery()
                            .eq(BusinessChatDialogueData::getDialogueCode, conversationId)
                            .eq(BusinessChatDialogueData::getStatus, NORMAL_STATUS)
                            .last("limit 1"));
            if (dialogueData == null) {
                throw new BaseException(
                        BusinessChatErrorCode.CHAT_SESSION_NOT_FOUND,
                        "conversationId was not found: " + conversationId);
            }

            // 会话删除按 conversationId 贯穿主表、轮次、摘要和阶段明细，统一软删后查询链路自然不可见。
            businessChatDialogueMapper.update(
                    null,
                    Wrappers.<BusinessChatDialogueData>update()
                            .eq("dialogue_code", conversationId)
                            .eq("status", NORMAL_STATUS)
                            .set("status", DELETED_STATUS));
            businessChatExchangeMapper.update(
                    null,
                    Wrappers.<BusinessChatExchangeData>update()
                            .eq("dialogue_code", conversationId)
                            .eq("status", NORMAL_STATUS)
                            .set("status", DELETED_STATUS));
            businessChatMemorySummaryMapper.update(
                    null,
                    Wrappers.<BusinessChatMemorySummaryData>update()
                            .eq("dialogue_code", conversationId)
                            .eq("status", NORMAL_STATUS)
                            .set("status", DELETED_STATUS));
            businessChatExchangeTraceStageMapper.update(
                    null,
                    Wrappers.<BusinessChatExchangeTraceStageData>update()
                            .eq("dialogue_code", conversationId)
                            .eq("status", NORMAL_STATUS)
                            .set("status", DELETED_STATUS));
            businessChatSessionStateService.clearIfActive(conversationId);
        } finally {
            boolean released = redisLeaseManager.release(leaseKey, ownerToken);
            if (!released) {
                log.error("Conversation delete lease release failed. conversationId={}, leaseKey={}",
                        conversationId,
                        leaseKey);
            }
        }
    }

    private String normalizeConversationId(String conversationId) {
        return BusinessInputValidator.normalizeRequiredText(conversationId, "conversationId");
    }
}
