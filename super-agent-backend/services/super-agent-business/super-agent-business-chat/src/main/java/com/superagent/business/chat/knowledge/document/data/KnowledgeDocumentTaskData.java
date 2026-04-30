package com.superagent.business.chat.knowledge.document.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.superagent.common.web.database.BaseTableData;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("super_agent_document_task")
public class KnowledgeDocumentTaskData extends BaseTableData {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long documentId;

    private Long planId;

    private Integer taskType;

    private Integer taskStatus;

    private Integer currentStage;

    private Integer triggerSource;

    private String strategySnapshot;

    private Integer retryCount;

    private java.time.LocalDateTime startTime;

    private java.time.LocalDateTime finishTime;

    private Long costMillis;

    private String errorCode;

    private String errorMsg;

    private String extJson;

    private Integer status;
}
