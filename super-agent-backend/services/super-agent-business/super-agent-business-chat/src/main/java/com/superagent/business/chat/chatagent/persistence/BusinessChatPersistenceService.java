package com.superagent.business.chat.chatagent.persistence;

import com.superagent.business.chat.chatagent.model.BusinessChatStartPlan;
import com.superagent.business.chat.chatagent.model.BusinessChatTaskInfo;
import com.superagent.business.chat.chatagent.runtime.BusinessChatFinalizedTurn;
import com.superagent.business.chat.chatagent.runtime.BusinessChatRuntimeContext;

/**
 * 对话主链路的持久化边界。
 *
 * <p>负责 dialogue、exchange、trace stage 和会话摘要的数据写入。</p>
 */
public interface BusinessChatPersistenceService {

    /**
     * 创建 RUNNING dialogue/exchange 并生成任务快照。
     *
     * @param startPlan 本轮对话启动计划
     * @return 后续运行、归档、释放资源所需的任务快照
     */
    BusinessChatTaskInfo createTurnRecordAndBuildTaskInfo(BusinessChatStartPlan startPlan);

    /**
     * 用收尾冻结快照归档成功轮次。
     *
     * @param finalizedTurn 本轮收尾冻结快照
     */
    void archiveSucceededTurn(BusinessChatFinalizedTurn finalizedTurn);

    boolean dialogueTitleExists(BusinessChatFinalizedTurn finalizedTurn);

    void updateDialogueTitleIfAbsent(BusinessChatFinalizedTurn finalizedTurn, String dialogueTitle);

    /**
     * 用当前运行态归档失败轮次。
     *
     * @param runtimeContext 本轮运行上下文
     * @param finishNote 终态备注
     */
    void archiveFailedTurn(BusinessChatRuntimeContext runtimeContext, String finishNote);

    void archiveStoppedTurn(BusinessChatRuntimeContext runtimeContext, String finishNote);

    /**
     * 用成功轮次快照刷新会话摘要。
     *
     * @param finalizedTurn 本轮收尾冻结快照
     */
    void refreshConversationSummary(BusinessChatFinalizedTurn finalizedTurn);

    Long startTraceStage(BusinessChatRuntimeContext runtimeContext, String stageCode, String stageName, int stageOrder);

    void completeTraceStage(Long traceStageId, String summaryText, Object snapshot);

    void failTraceStage(Long traceStageId, Throwable error);
}
