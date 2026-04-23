package com.superagent.business.chat.chatagent.finalization;

import java.util.List;

public record BusinessChatFinalizationResult(
        String dialogueTitle,
        List<String> followUpSuggestionList) {
}
