package com.superagent.business.chat.chatagent.persistence.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.superagent.common.web.database.BaseTableData;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("super_agent_chat_session_state")
public class BusinessChatSessionStateData extends BaseTableData {

    @TableId(type = IdType.INPUT)
    private Long id;

    private String stateKey;

    private String activeConversationId;

    private Integer status;
}
