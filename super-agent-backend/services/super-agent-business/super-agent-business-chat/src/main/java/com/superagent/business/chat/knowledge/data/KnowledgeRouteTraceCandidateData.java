package com.superagent.business.chat.knowledge.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.superagent.common.web.database.BaseTableData;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("super_agent_knowledge_route_trace_candidate")
public class KnowledgeRouteTraceCandidateData extends BaseTableData {

    @TableId(type = IdType.INPUT)
    private Long id;

    private String traceId;

    private String candidateType;

    private String candidateId;

    private String candidateName;

    private Double score;

    private String hitReason;

    private Integer rankNo;

    private Integer status;
}
