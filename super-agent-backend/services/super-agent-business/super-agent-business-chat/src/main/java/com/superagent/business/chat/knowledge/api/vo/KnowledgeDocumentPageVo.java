package com.superagent.business.chat.knowledge.api.vo;

import java.util.List;
import lombok.Data;

@Data
public class KnowledgeDocumentPageVo {

    private int pageNo;

    private int pageSize;

    private long totalSize;

    private long totalPages;

    private List<KnowledgeDocumentVo> documents;
}
