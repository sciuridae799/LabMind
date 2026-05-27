package com.superagent.business.chat.chatagent.persistence.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.superagent.common.web.database.BaseTableData;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("super_agent_chat_exchange_trace_stage")
public class BusinessChatExchangeTraceStageData extends BaseTableData {

    @TableId(type = IdType.INPUT)
    private Long id;

    private String dialogueCode;

    private String workspaceId;

    private Long exchangeId;

    private String traceId;

    private String stageCode;

    private String stageName;

    private Integer stageOrder;

    private Integer stageLevel;

    private Long parentStageId;

    private String executionMode;

    private Integer stageState;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Long durationMs;

    private String summaryText;

    private String errorMessage;

    private String snapshotJson;

    private Integer status;
}
