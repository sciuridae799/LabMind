package com.superagent.business.chat.chatagent.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.superagent.business.chat.chatagent.persistence.data.BusinessChatSessionStateData;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface BusinessChatSessionStateMapper extends BaseMapper<BusinessChatSessionStateData> {

    @Insert({
            "INSERT INTO super_agent_chat_session_state (",
            "    id, state_key, workspace_id, auth_session_token, active_conversation_id,",
            "    create_time, edit_time, status",
            ") VALUES (",
            "    #{state.id}, #{state.stateKey}, #{state.workspaceId}, #{state.authSessionToken},",
            "    #{state.activeConversationId}, NOW(), NOW(), #{state.status}",
            ")",
            "ON DUPLICATE KEY UPDATE",
            "    active_conversation_id = #{state.activeConversationId},",
            "    edit_time = NOW(),",
            "    status = #{state.status}"
    })
    int upsertActiveConversation(@Param("state") BusinessChatSessionStateData state);
}
