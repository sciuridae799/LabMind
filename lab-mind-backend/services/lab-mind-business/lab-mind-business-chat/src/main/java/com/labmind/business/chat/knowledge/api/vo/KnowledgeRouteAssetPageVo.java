package com.labmind.business.chat.knowledge.api.vo;

import java.util.List;
import lombok.Data;

@Data
public class KnowledgeRouteAssetPageVo {

    private int pageNo;

    private int pageSize;

    private long totalSize;

    private long totalPages;

    private List<KnowledgeRouteAssetVo> assets;
}
