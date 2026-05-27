package com.superagent.business.chat.knowledge.profile.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.superagent.common.web.database.BaseTableData;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("super_agent_knowledge_document_profile")
public class KnowledgeDocumentProfileData extends BaseTableData {

    @TableId(type = IdType.INPUT)
    private Long id;

    private Long documentId;

    private String workspaceId;

    private String scopeCode;

    private String topicCode;

    private Integer profileStatus;

    private String summaryText;

    private String answerableQuestionsJson;

    private String unanswerableQuestionsJson;

    private String businessEntitiesJson;

    private String termsJson;

    private String questionPatternsJson;

    private Integer profileVersion;

    private Integer status;
}
