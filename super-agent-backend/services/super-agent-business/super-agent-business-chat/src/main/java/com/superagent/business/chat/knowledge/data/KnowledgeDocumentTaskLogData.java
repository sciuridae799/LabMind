package com.superagent.business.chat.knowledge.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.superagent.common.web.database.BaseTableData;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("super_agent_document_task_log")
public class KnowledgeDocumentTaskLogData extends BaseTableData {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long taskId;

    private Long documentId;

    private Integer stageType;

    private Integer eventType;

    private Integer logLevel;

    private Integer operatorType;

    private Long operatorId;

    private String content;

    private String detailJson;

    private Integer status;
}
