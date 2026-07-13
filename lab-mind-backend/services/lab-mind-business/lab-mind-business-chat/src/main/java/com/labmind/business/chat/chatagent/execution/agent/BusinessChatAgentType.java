package com.labmind.business.chat.chatagent.execution.agent;

import lombok.Getter;

/**
 * Agent 业务角色枚举。
 *
 * <p>这里描述的是“由谁负责本轮执行”，不是模型供应商、执行模式或工具能力。
 * 编排层根据意图、澄清结果和路由结果选择 Agent 类型；注册表再用该类型定位具体 Agent 实现。</p>
 *
 * <p>当前主链路实际落点：</p>
 * <ul>
 *     <li>需要追问用户时选择 {@link #CLARIFICATION}。</li>
 *     <li>知识库问答选择 {@link #KNOWLEDGE_QA}。</li>
 *     <li>当前文档和开放问答选择 {@link #THINK_ACT}。</li>
 * </ul>
 */
@Getter
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

}
