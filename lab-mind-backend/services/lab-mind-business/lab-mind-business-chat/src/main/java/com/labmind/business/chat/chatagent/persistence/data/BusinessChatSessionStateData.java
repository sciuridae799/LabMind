package com.labmind.business.chat.chatagent.persistence.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.labmind.common.web.database.BaseTableData;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lab_mind_chat_session_state")
public class BusinessChatSessionStateData extends BaseTableData {

    @TableId(type = IdType.INPUT)
    private Long id;

    private String stateKey;

    private String workspaceId;

    private String authSessionToken;

    private String activeConversationId;

    private Integer status;
}
