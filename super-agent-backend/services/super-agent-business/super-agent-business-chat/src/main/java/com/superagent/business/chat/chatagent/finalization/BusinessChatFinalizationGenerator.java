package com.superagent.business.chat.chatagent.finalization;

import com.superagent.business.chat.chatagent.runtime.BusinessChatFinalizedTurn;

public interface BusinessChatFinalizationGenerator {

    BusinessChatFinalizationResult generate(BusinessChatFinalizedTurn finalizedTurn, boolean titleRequired);
}
