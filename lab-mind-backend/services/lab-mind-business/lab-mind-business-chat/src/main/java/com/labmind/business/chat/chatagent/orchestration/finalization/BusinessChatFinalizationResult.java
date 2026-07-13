package com.labmind.business.chat.chatagent.orchestration.finalization;

import java.util.List;

public record BusinessChatFinalizationResult(
        String dialogueTitle,
        List<String> followUpSuggestionList) {
}
