package com.superagent.business.chat.chatagent.persistence.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.superagent.common.web.database.BaseTableData;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("super_agent_chat_dialogue")
public class BusinessChatDialogueData extends BaseTableData {

    @TableId(type = IdType.INPUT)
    private Long id;

    private String dialogueCode;

    private String workspaceId;

    private String authSessionToken;

    private String dialogueTitle;

    private Integer dialogueStage;

    private Integer chatMode;

    private Long selectedDocumentId;

    private String selectedDocumentName;

    private Integer status;
}
