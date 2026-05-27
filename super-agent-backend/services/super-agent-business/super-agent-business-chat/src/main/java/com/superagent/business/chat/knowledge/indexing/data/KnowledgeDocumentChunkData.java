package com.superagent.business.chat.knowledge.indexing.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.superagent.common.web.database.BaseTableData;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("super_agent_document_chunk")
public class KnowledgeDocumentChunkData extends BaseTableData {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long documentId;

    private String workspaceId;

    private Long taskId;

    private Long planId;

    private Long parentBlockId;

    private Integer chunkNo;

    private Integer sourceType;

    private String sectionPath;

    private Long structureNodeId;

    private Integer structureNodeType;

    private String canonicalPath;

    private Integer itemIndex;

    private String chunkText;

    private Integer charCount;

    private Integer tokenCount;

    private Integer vectorStatus;

    private Integer vectorStoreType;

    private String vectorId;

    private Integer status;
}
