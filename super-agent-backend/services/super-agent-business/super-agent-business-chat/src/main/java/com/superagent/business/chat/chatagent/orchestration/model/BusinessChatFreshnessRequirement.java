package com.superagent.business.chat.chatagent.orchestration.model;

import java.util.List;

/**
 * 本轮问题的时效性要求。
 *
 * <p>required 表示用户是否在问“今天、当前、最新”等实时信息；capability 表示当前执行链路是否具备实时检索能力。</p>
 */
public record BusinessChatFreshnessRequirement(
        boolean required,
        String reason,
        List<String> matchedSignalList,
        String capability) {
}
