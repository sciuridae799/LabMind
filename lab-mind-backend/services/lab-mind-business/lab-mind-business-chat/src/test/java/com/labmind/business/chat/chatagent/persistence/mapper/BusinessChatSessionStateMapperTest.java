package com.labmind.business.chat.chatagent.persistence.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.labmind.business.chat.chatagent.persistence.data.BusinessChatSessionStateData;
import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Insert;
import org.junit.jupiter.api.Test;

class BusinessChatSessionStateMapperTest {

    @Test
    void shouldAtomicallyUpsertTheUniqueChatPageState() throws Exception {
        Method method = BusinessChatSessionStateMapper.class.getMethod(
                "upsertActiveConversation",
                BusinessChatSessionStateData.class);
        Insert insert = method.getAnnotation(Insert.class);

        assertThat(insert).isNotNull();
        String sql = String.join(" ", insert.value());
        assertThat(sql)
                .containsIgnoringCase("INSERT INTO lab_mind_chat_session_state")
                .contains("state_key", "workspace_id", "auth_session_token")
                .containsIgnoringCase("ON DUPLICATE KEY UPDATE")
                .contains("active_conversation_id = #{state.activeConversationId}")
                .contains("status = #{state.status}");
    }
}
