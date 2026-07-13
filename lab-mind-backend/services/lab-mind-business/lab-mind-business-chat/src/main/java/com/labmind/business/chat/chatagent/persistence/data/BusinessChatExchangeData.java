package com.labmind.business.chat.chatagent.persistence.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.labmind.common.web.database.BaseTableData;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lab_mind_chat_exchange")
public class BusinessChatExchangeData extends BaseTableData {

    @TableId(type = IdType.INPUT)
    private Long id;

    private String dialogueCode;

    private String workspaceId;

    private String userPrompt;

    private String replyContent;

    private String reasoningNoteList;

    private String sourceSnapshotList;

    private String followupSuggestionList;

    private String toolTraceList;

    private String debugTraceJson;

    private Integer exchangeState;

    private String finishNote;

    private Long firstTokenLatencyMs;

    private Long totalLatencyMs;

    private Integer status;
}
