package com.superagent.business.chat.knowledge.route.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.superagent.common.web.database.BaseTableData;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("super_agent_knowledge_scope_node")
public class KnowledgeScopeNodeData extends BaseTableData {

    @TableId(type = IdType.INPUT)
    private Long id;

    private String scopeCode;

    private String scopeName;

    private String parentScopeCode;

    private String description;

    private String aliases;

    private String examples;

    private Integer sortOrder;

    private Integer status;
}
