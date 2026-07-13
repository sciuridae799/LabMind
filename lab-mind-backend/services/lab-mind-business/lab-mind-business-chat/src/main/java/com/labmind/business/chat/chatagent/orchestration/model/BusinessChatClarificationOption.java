package com.labmind.business.chat.chatagent.orchestration.model;

public record BusinessChatClarificationOption(
        Long documentId,
        String documentName,
        String scopeCode,
        String scopeName,
        String topicCode,
        String topicName,
        double score) {
}
