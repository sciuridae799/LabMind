package com.labmind.business.chat.chatagent.service.impl;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.mysql.MysqlSaver;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.labmind.business.chat.chatagent.persistence.data.BusinessChatDialogueData;
import com.labmind.business.chat.chatagent.persistence.data.BusinessChatExchangeData;
import com.labmind.business.chat.chatagent.persistence.data.BusinessChatExchangeTraceStageData;
import com.labmind.business.chat.chatagent.persistence.data.BusinessChatMemorySummaryData;
import com.labmind.business.chat.chatagent.persistence.data.BusinessChatModelCallTraceData;
import com.labmind.business.chat.chatagent.persistence.data.BusinessChatToolCallTraceData;
import com.labmind.business.chat.chatagent.api.dto.BusinessChatDeleteSessionRequest;
import com.labmind.business.chat.chatagent.persistence.mapper.BusinessChatDialogueMapper;
import com.labmind.business.chat.chatagent.persistence.mapper.BusinessChatExchangeMapper;
import com.labmind.business.chat.chatagent.persistence.mapper.BusinessChatExchangeTraceStageMapper;
import com.labmind.business.chat.chatagent.persistence.mapper.BusinessChatMemorySummaryMapper;
import com.labmind.business.chat.chatagent.persistence.mapper.BusinessChatModelCallTraceMapper;
import com.labmind.business.chat.chatagent.persistence.mapper.BusinessChatToolCallTraceMapper;
import com.labmind.business.chat.chatagent.runtime.BusinessChatAgentCounterKeys;
import com.labmind.business.chat.chatagent.runtime.BusinessChatConversationLeaseKeys;
import com.labmind.business.chat.chatagent.service.BusinessChatErrorCode;
import com.labmind.business.chat.chatagent.service.BusinessChatSessionService;
import com.labmind.business.chat.chatagent.service.BusinessChatSessionStateService;
import com.labmind.business.chat.auth.AuthRole;
import com.labmind.business.chat.auth.AuthSessionContext;
import com.labmind.business.chat.auth.AuthSessionHolder;
import com.labmind.business.chat.support.BusinessInputValidator;
import com.labmind.common.frame.exception.BaseException;
import com.labmind.redisson.servicelease.lease.RedisLeaseManager;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 会话生命周期管理服务。
 *
 * <p>当前只承载会话删除：删除前获取同一把会话租约，删除时按 conversationId 统一软删会话归档与调用轨迹，
 * 并释放 Graph checkpoint thread。</p>
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

    private final BusinessChatModelCallTraceMapper businessChatModelCallTraceMapper;

    private final BusinessChatToolCallTraceMapper businessChatToolCallTraceMapper;

    private final BusinessChatSessionStateService businessChatSessionStateService;

    private final MysqlSaver businessChatCheckpointSaver;

    private final RedissonClient redissonClient;

    @Override
    @Transactional
    public void deleteSession(BusinessChatDeleteSessionRequest request) {
        String conversationId = normalizeConversationId(request.getConversationId());
        String workspaceId = BusinessInputValidator.normalizeRequiredText(request.getWorkspaceId(), "workspaceId");
        String authSessionToken = currentAuthSessionToken();
        String leaseKey = BusinessChatConversationLeaseKeys.conversationLeaseKey(conversationId);
        String ownerToken = UUID.randomUUID().toString();
        // 删除和流式生成共用同一把会话锁，保证不会一边写 RUNNING exchange，一边把会话归档软删。
        boolean acquired = redisLeaseManager.acquire(leaseKey, ownerToken, DELETE_LEASE_TTL);
        if (!acquired) {
            throw new BaseException(
                    BusinessChatErrorCode.CHAT_SESSION_RUNNING,
                    "conversation is running and cannot be deleted: " + conversationId);
        }
        registerLeaseReleaseAfterTransaction(conversationId, leaseKey, ownerToken);

        BusinessChatDialogueData dialogueData = businessChatDialogueMapper.selectOne(
                Wrappers.<BusinessChatDialogueData>lambdaQuery()
                        .eq(BusinessChatDialogueData::getDialogueCode, conversationId)
                        .eq(BusinessChatDialogueData::getWorkspaceId, workspaceId)
                        .eq(BusinessChatDialogueData::getAuthSessionToken, authSessionToken)
                        .eq(BusinessChatDialogueData::getStatus, NORMAL_STATUS)
                        .last("limit 1"));
        if (dialogueData == null) {
            throw new BaseException(
                    BusinessChatErrorCode.CHAT_SESSION_NOT_FOUND,
                    "conversationId was not found: " + conversationId);
        }

        // 会话删除按 conversationId 贯穿归档、调用轨迹和 Graph thread，避免任何执行状态脱离会话生命周期。
        businessChatDialogueMapper.update(
                null,
                Wrappers.<BusinessChatDialogueData>update()
                        .eq("dialogue_code", conversationId)
                        .eq("workspace_id", workspaceId)
                        .eq("auth_session_token", authSessionToken)
                        .eq("status", NORMAL_STATUS)
                        .set("status", DELETED_STATUS));
        businessChatExchangeMapper.update(
                null,
                Wrappers.<BusinessChatExchangeData>update()
                        .eq("dialogue_code", conversationId)
                        .eq("workspace_id", workspaceId)
                        .eq("status", NORMAL_STATUS)
                        .set("status", DELETED_STATUS));
        businessChatMemorySummaryMapper.update(
                null,
                Wrappers.<BusinessChatMemorySummaryData>update()
                        .eq("dialogue_code", conversationId)
                        .eq("workspace_id", workspaceId)
                        .eq("status", NORMAL_STATUS)
                        .set("status", DELETED_STATUS));
        businessChatExchangeTraceStageMapper.update(
                null,
                Wrappers.<BusinessChatExchangeTraceStageData>update()
                        .eq("dialogue_code", conversationId)
                        .eq("workspace_id", workspaceId)
                        .eq("status", NORMAL_STATUS)
                        .set("status", DELETED_STATUS));
        businessChatModelCallTraceMapper.update(
                null,
                Wrappers.<BusinessChatModelCallTraceData>update()
                        .eq("dialogue_code", conversationId)
                        .eq("status", NORMAL_STATUS)
                        .set("status", DELETED_STATUS));
        businessChatToolCallTraceMapper.update(
                null,
                Wrappers.<BusinessChatToolCallTraceData>update()
                        .eq("dialogue_code", conversationId)
                        .eq("status", NORMAL_STATUS)
                        .set("status", DELETED_STATUS));
        businessChatSessionStateService.clearIfActive(conversationId, workspaceId, authSessionToken);
        releaseGraphCheckpoints(conversationId);
        redissonClient.getKeys().delete(BusinessChatAgentCounterKeys.conversationCounterKeys(conversationId));
    }

    private String normalizeConversationId(String conversationId) {
        return BusinessInputValidator.normalizeRequiredText(conversationId, "conversationId");
    }

    private void registerLeaseReleaseAfterTransaction(String conversationId, String leaseKey, String ownerToken) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            releaseConversationLease(conversationId, leaseKey, ownerToken);
            throw new IllegalStateException("transaction synchronization is required for conversation deletion");
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                releaseConversationLease(conversationId, leaseKey, ownerToken);
            }
        });
    }

    private void releaseConversationLease(String conversationId, String leaseKey, String ownerToken) {
        boolean released = redisLeaseManager.release(leaseKey, ownerToken);
        if (!released) {
            log.error("Conversation lease release failed after delete transaction. conversationId={}, leaseKey={}",
                    conversationId,
                    leaseKey);
        }
    }

    private void releaseGraphCheckpoints(String conversationId) {
        RunnableConfig runnableConfig = RunnableConfig.builder()
                .threadId(conversationId)
                .build();
        try {
            if (businessChatCheckpointSaver.list(runnableConfig).isEmpty()) {
                return;
            }
            businessChatCheckpointSaver.release(runnableConfig);
        } catch (Exception error) {
            throw new IllegalStateException(
                    "failed to release graph checkpoints for conversation: " + conversationId,
                    error);
        }
    }

    private String currentAuthSessionToken() {
        AuthSessionContext session = AuthSessionHolder.required();
        return session.role() == AuthRole.GUEST ? session.token() : "";
    }
}
