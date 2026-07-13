package com.labmind.business.chat.knowledge.indexing.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.labmind.common.web.database.BaseTableData;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("lab_mind_document_parent_block")
public class KnowledgeDocumentParentBlockData extends BaseTableData {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long documentId;

    private String workspaceId;

    private Long taskId;

    private Long planId;

    private Integer parentNo;

    private Integer sourceType;

    private String sectionPath;

    private Long structureNodeId;

    private Integer structureNodeType;

    private String canonicalPath;

    private Integer itemIndex;

    private String parentText;

    private Integer charCount;

    private Integer tokenCount;

    private Integer childCount;

    private Integer startChunkNo;

    private Integer endChunkNo;

    private Integer status;
}
