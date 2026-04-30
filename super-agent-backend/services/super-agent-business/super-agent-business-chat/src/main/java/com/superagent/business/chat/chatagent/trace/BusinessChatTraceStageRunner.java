package com.superagent.business.chat.chatagent.trace;

import com.superagent.business.chat.chatagent.persistence.BusinessChatPersistenceService;
import com.superagent.business.chat.chatagent.runtime.BusinessChatRuntimeContext;
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
        try {
            traceStageId = start(runtimeContext, stage);
            T value = supplier.get();
            complete(traceStageId, summaryBuilder.apply(value), snapshotBuilder.apply(value));
            return value;
        } catch (Throwable error) {
            fail(traceStageId, error);
            throw propagate(error);
        }
    }

    public Long start(BusinessChatRuntimeContext runtimeContext, BusinessChatTraceStage stage) {
        return businessChatPersistenceService.startTraceStage(
                runtimeContext,
                stage.code(),
                stage.stageName(),
                stage.order());
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
