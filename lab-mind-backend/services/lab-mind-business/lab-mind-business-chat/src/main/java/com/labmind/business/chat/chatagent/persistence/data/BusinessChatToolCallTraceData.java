package com.labmind.business.chat.chatagent.persistence.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.labmind.common.web.database.BaseTableData;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lab_mind_chat_tool_call_trace")
public class BusinessChatToolCallTraceData extends BaseTableData {

    @TableId(type = IdType.INPUT)
    private Long id;

    private String dialogueCode;

    private Long exchangeId;

    private String traceId;

    private String toolName;

    private Integer callState;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Long durationMs;

    private String errorMessage;

    private Integer status;
}
