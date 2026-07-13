package com.labmind.business.chat.chatagent.orchestration.model;

import java.util.List;

public record BusinessChatClarificationPlan(
        boolean required,
        String reason,
        String reply,
        List<BusinessChatClarificationOption> optionList) {

    public static BusinessChatClarificationPlan notRequired() {
        return new BusinessChatClarificationPlan(false, "知识路由候选稳定", null, List.of());
    }
}
