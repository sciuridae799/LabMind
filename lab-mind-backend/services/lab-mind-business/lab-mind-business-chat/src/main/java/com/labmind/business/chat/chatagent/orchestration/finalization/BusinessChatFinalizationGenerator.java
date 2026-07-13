package com.labmind.business.chat.chatagent.orchestration.finalization;

import com.labmind.business.chat.chatagent.runtime.BusinessChatFinalizedTurn;
import com.labmind.business.chat.chatagent.runtime.BusinessChatRuntimeContext;

public interface BusinessChatFinalizationGenerator {

    BusinessChatFinalizationResult generate(
            BusinessChatRuntimeContext runtimeContext,
            BusinessChatFinalizedTurn finalizedTurn,
            boolean titleRequired);
}
