package com.superagent.business.chat.chatagent.trace;

/**
 * 单轮对话可观测阶段定义。
 *
 * <p>阶段顺序必须对应真实执行链路，前端只按这里写入的事实展示时间线。</p>
 */
public enum BusinessChatTraceStage {

    MEMORY_LOAD("MEMORY_LOAD", "会话记忆加载", 100),
    QUESTION_REWRITE("QUESTION_REWRITE", "问题改写", 200),
    FRESHNESS_DETECTION("FRESHNESS_DETECTION", "时效性判断", 250),
    ROUTE_DECISION("ROUTE_DECISION", "路由判定", 300),
    GRAPH_QUERY("GRAPH_QUERY", "结构图查询", 400),
    DOCUMENT_CONTEXT("DOCUMENT_CONTEXT", "文档画像加载", 450),
    EVIDENCE_RETRIEVAL("EVIDENCE_RETRIEVAL", "证据检索", 500),
    ANSWER_GENERATION("ANSWER_GENERATION", "回答生成", 700),
    RECOMMENDATION("RECOMMENDATION", "推荐问题生成", 800),
    FINALIZE("FINALIZE", "收尾归档", 900);

    private final String code;

    private final String stageName;

    private final int order;

    BusinessChatTraceStage(String code, String stageName, int order) {
        this.code = code;
        this.stageName = stageName;
        this.order = order;
    }

    public String code() {
        return code;
    }

    public String stageName() {
        return stageName;
    }

    public int order() {
        return order;
    }
}
