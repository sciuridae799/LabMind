package com.superagent.business.chat.chatagent.agent;

public enum BusinessChatAgentType {

    CLARIFICATION("CLARIFICATION", "歧义澄清执行器"),
    REACT("REACT", "ReAct Agent 执行器"),
    KNOWLEDGE_QA("KNOWLEDGE_QA", "知识问答执行器"),
    EVIDENCE_GENERATION("EVIDENCE_GENERATION", "证据驱动生成"),
    CITATION("CITATION", "引用来源"),
    BUDGET_CONTROL("BUDGET_CONTROL", "预算控制"),
    MULTI_STEP_REASONING("MULTI_STEP_REASONING", "多步智能推理"),
    THINK_ACT("THINK_ACT", "思考-行动模型");

    private final String value;

    private final String displayName;

    BusinessChatAgentType(String value, String displayName) {
        this.value = value;
        this.displayName = displayName;
    }

    public String getValue() {
        return value;
    }

    public String getDisplayName() {
        return displayName;
    }
}
