package com.superagent.business.chat.knowledge.route.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.superagent.common.web.database.BaseTableData;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 文档结构节点表实体。
 *
 * <p>每次解析任务会生成一棵稳定的文档结构树，供 Neo4j 结构图和后续结构查询复用。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("super_agent_document_structure_node")
public class KnowledgeDocumentStructureNodeData extends BaseTableData {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long documentId;

    private Long parseTaskId;

    private Integer nodeNo;

    private Integer nodeType;

    private Long parentNodeId;

    private Long prevSiblingNodeId;

    private Long nextSiblingNodeId;

    private Integer depth;

    private String nodeCode;

    private String title;

    private String anchorText;

    private String canonicalPath;

    private String sectionPath;

    private String contentText;

    private Integer itemIndex;

    private Integer status;
}
