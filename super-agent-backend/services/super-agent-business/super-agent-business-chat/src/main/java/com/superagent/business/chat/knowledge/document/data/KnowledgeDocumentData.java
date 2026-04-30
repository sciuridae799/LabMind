package com.superagent.business.chat.knowledge.document.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.superagent.common.web.database.BaseTableData;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("super_agent_document")
public class KnowledgeDocumentData extends BaseTableData {

    @TableId(type = IdType.INPUT)
    private Long id;

    private String documentName;

    private String originalFileName;

    private Integer fileType;

    private String mimeType;

    private Long fileSize;

    private Integer storageType;

    private String bucketName;

    private String objectName;

    private String objectUrl;

    private Integer parseStatus;

    private Integer strategyStatus;

    private Integer indexStatus;

    private Integer charCount;

    private Integer tokenCount;

    private Integer structureLevel;

    private Integer contentQualityLevel;

    private String parseTextPath;

    private String parseErrorMsg;

    private String knowledgeScopeCode;

    private String knowledgeScopeName;

    private String businessCategory;

    private String documentTags;

    private Long currentPlanId;

    private Long lastParseTaskId;

    private Integer structureNodeCount;

    private Long lastIndexTaskId;

    private Integer status;
}
