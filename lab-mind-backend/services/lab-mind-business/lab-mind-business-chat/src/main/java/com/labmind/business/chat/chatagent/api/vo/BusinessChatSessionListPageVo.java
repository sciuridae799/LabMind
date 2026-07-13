package com.labmind.business.chat.chatagent.api.vo;

import java.util.List;
import lombok.Data;

@Data
public class BusinessChatSessionListPageVo {

    private long pageNo;

    private long pageSize;

    private long totalSize;

    private long totalPages;

    private List<BusinessChatSessionListItemVo> sessions;
}
