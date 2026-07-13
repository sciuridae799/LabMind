package com.labmind.business.chat.chatagent.execution.agent;

/**
 * Agent 注册表。
 *
 * <p>服务层不直接依赖具体 Agent 类，而是通过编排计划中的 {@link BusinessChatAgentType}
 * 取到对应实现。注册表必须保证类型到实现的映射明确且唯一，缺失和重复都属于启动或执行期配置错误。</p>
 */
public interface BusinessChatAgentRegistry {

    /**
     * 获取指定类型的 Agent。
     *
     * <p>调用方已经完成业务路由，因此这里不做兜底选择；如果类型为空或未注册，应直接暴露配置问题。</p>
     */
    BusinessChatAgent getRequiredAgent(BusinessChatAgentType agentType);
}
