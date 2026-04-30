package com.superagent.business.chat.knowledge.indexing.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.superagent.common.web.database.BaseTableData;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("super_agent_document_strategy_plan")
public class KnowledgeDocumentStrategyPlanData extends BaseTableData {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long documentId;

    private Integer planVersion;

    private Integer planSource;

    private Integer planStatus;

    private Integer strategyCount;

    private String strategySnapshot;

    private String recommendReason;

    private String adjustNote;

    private Long confirmUserId;

    private LocalDateTime confirmTime;

    private Integer status;
}
