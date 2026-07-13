package com.labmind.business.chat.chatagent.persistence.model;

public enum BusinessChatEventType {
    AGENT_STARTED,
    AGENT_FINISHED,
    EXECUTION_PROGRESS,
    TEXT_DELTA,
    FUNCTION_SUPPLEMENT,
    REFERENCE_SUPPLEMENT,
    FOLLOW_UP_RECOMMENDATION,
    TURN_REJECTED,
    TURN_FINISHED,
    TURN_FAILED
}
