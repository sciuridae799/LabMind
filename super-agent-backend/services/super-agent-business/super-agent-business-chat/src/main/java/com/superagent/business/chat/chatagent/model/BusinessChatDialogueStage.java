package com.superagent.business.chat.chatagent.model;

import java.util.Arrays;
import lombok.Getter;

@Getter
public enum BusinessChatDialogueStage {
    IDLE(1, "IDLE"),
    RUNNING(2, "RUNNING");

    private final int databaseCode;

    private final String value;

    BusinessChatDialogueStage(int databaseCode, String value) {
        this.databaseCode = databaseCode;
        this.value = value;
    }

    public static BusinessChatDialogueStage fromDatabaseCode(Integer databaseCode) {
        if (databaseCode == null) {
            throw new IllegalStateException("dialogueStage databaseCode is null");
        }
        return Arrays.stream(values())
                .filter(stage -> stage.databaseCode == databaseCode)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException(
                        "dialogueStage databaseCode is invalid: " + databaseCode));
    }
}
