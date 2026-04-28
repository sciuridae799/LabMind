package com.superagent.business.chat.knowledge.vo;

import java.util.List;
import lombok.Data;

@Data
public class KnowledgeRouteTracePageVo {

    private int pageNo;

    private int pageSize;

    private long totalSize;

    private long totalPages;

    private List<KnowledgeRouteTraceVo> traces;
}
