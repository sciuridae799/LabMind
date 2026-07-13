package com.labmind.business.chat.chatagent.trace;

import com.labmind.business.chat.chatagent.persistence.BusinessChatPersistenceService;
import com.labmind.business.chat.chatagent.runtime.BusinessChatRuntimeContext;
import java.util.function.Function;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 对话阶段追踪执行器。
 *
 * <p>它只负责把真实业务步骤包成 RUNNING/COMPLETED/FAILED 阶段记录，不改变业务分支。</p>
 */
@Service
@RequiredArgsConstructor
public class BusinessChatTraceStageRunner {

    private final BusinessChatPersistenceService businessChatPersistenceService;

    public <T> T run(
            BusinessChatRuntimeContext runtimeContext,
            BusinessChatTraceStage stage,
            Supplier<T> supplier,
            Function<T, Object> snapshotBuilder,
            Function<T, String> summaryBuilder) {
        Long traceStageId = null;
        String previousStageCode = runtimeContext.getCurrentTraceStageCode();
        String previousStageName = runtimeContext.getCurrentTraceStageName();
        try {
            traceStageId = start(runtimeContext, stage);
            runtimeContext.bindCurrentTraceStage(stage.code(), stage.stageName());
            T value = supplier.get();
            complete(traceStageId, summaryBuilder.apply(value), snapshotBuilder.apply(value));
            return value;
        } catch (Throwable error) {
            fail(traceStageId, error);
            throw propagate(error);
        } finally {
            if (previousStageCode == null && previousStageName == null) {
                runtimeContext.clearCurrentTraceStage();
            } else {
                runtimeContext.bindCurrentTraceStage(previousStageCode, previousStageName);
            }
        }
    }

    public Long start(BusinessChatRuntimeContext runtimeContext, BusinessChatTraceStage stage) {
        return businessChatPersistenceService.startTraceStage(
                runtimeContext,
                stage.code(),
                stage.stageName(),
                stage.order());
    }

    public Long startSubStage(
            BusinessChatRuntimeContext runtimeContext,
            Long parentStageId,
            String stageCode,
            String stageName,
            int stageOrder) {
        return businessChatPersistenceService.startTraceSubStage(
                runtimeContext,
                parentStageId,
                stageCode,
                stageName,
                stageOrder);
    }

    public void complete(Long traceStageId, String summaryText, Object snapshot) {
        businessChatPersistenceService.completeTraceStage(traceStageId, summaryText, snapshot);
    }

    public void fail(Long traceStageId, Throwable error) {
        businessChatPersistenceService.failTraceStage(traceStageId, error);
    }

    private RuntimeException propagate(Throwable error) {
        if (error instanceof RuntimeException runtimeException) {
            return runtimeException;
        }
        if (error instanceof Error fatalError) {
            throw fatalError;
        }
        return new IllegalStateException(error);
    }
}
