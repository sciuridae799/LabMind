package com.superagent.business.chat.chatagent.orchestration.finalization;

import com.superagent.business.chat.chatagent.runtime.BusinessChatFinalizedTurn;
import com.superagent.business.chat.chatagent.runtime.BusinessChatRuntimeContext;

public interface BusinessChatFinalizationGenerator {

    BusinessChatFinalizationResult generate(
            BusinessChatRuntimeContext runtimeContext,
            BusinessChatFinalizedTurn finalizedTurn,
            boolean titleRequired);
}
