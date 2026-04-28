package com.superagent.business.chat.knowledge.vo;

import java.util.List;
import lombok.Data;

@Data
public class KnowledgeRouteTraceVo {

    private String conversationId;

    private String exchangeId;

    private String question;

    private String knowledgeRoute;

    private List<KnowledgeRouteCandidateVo> candidates;

    private String createTime;
}
