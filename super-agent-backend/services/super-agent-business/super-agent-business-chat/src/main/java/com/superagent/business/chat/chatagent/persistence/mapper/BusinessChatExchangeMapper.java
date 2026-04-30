package com.superagent.business.chat.chatagent.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.superagent.business.chat.chatagent.persistence.data.BusinessChatExchangeData;
import com.superagent.business.chat.knowledge.route.model.KnowledgeRouteTraceRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 问答轮次表访问接口。
 *
 * <p>每条 exchange 对应会话中的一轮用户问题和助手回答，承载正文、追问、工具痕迹、状态和耗时等归档结果。</p>
 */
@Mapper
public interface BusinessChatExchangeMapper extends BaseMapper<BusinessChatExchangeData> {

    /**
     * 统计知识库模式下已经归档执行计划的问答轮次。
     *
     * <p>路由追踪页复盘的是当时写入 exchange.debug_trace_json 的计划，不重新执行知识路由。</p>
     */
    @Select({
            "<script>",
            "SELECT COUNT(1)",
            "FROM super_agent_chat_exchange e",
            "INNER JOIN super_agent_chat_dialogue d",
            "ON d.dialogue_code = e.dialogue_code",
            "AND d.status = #{status}",
            "WHERE e.status = #{status}",
            "  AND d.chat_mode = #{knowledgeBaseMode}",
            "  AND e.debug_trace_json IS NOT NULL",
            "<if test=\"keyword != null and keyword != ''\">",
            "  AND (e.user_prompt LIKE CONCAT('%', #{keyword}, '%')",
            "       OR e.dialogue_code LIKE CONCAT('%', #{keyword}, '%'))",
            "</if>",
            "</script>"
    })
    long countKnowledgeRouteTraceRows(
            @Param("keyword") String keyword,
            @Param("knowledgeBaseMode") Integer knowledgeBaseMode,
            @Param("status") Integer status);

    /**
     * 分页查询知识路由追踪快照。
     *
     * <p>这里只取前端复盘所需字段，候选文档明细由 service 从 debug_trace_json 中解析。</p>
     */
    @Select({
            "<script>",
            "SELECT",
            "  e.dialogue_code AS conversationId,",
            "  e.id AS exchangeId,",
            "  e.user_prompt AS question,",
            "  e.debug_trace_json AS debugTraceJson,",
            "  e.create_time AS createTime",
            "FROM super_agent_chat_exchange e",
            "INNER JOIN super_agent_chat_dialogue d",
            "ON d.dialogue_code = e.dialogue_code",
            "AND d.status = #{status}",
            "WHERE e.status = #{status}",
            "  AND d.chat_mode = #{knowledgeBaseMode}",
            "  AND e.debug_trace_json IS NOT NULL",
            "<if test=\"keyword != null and keyword != ''\">",
            "  AND (e.user_prompt LIKE CONCAT('%', #{keyword}, '%')",
            "       OR e.dialogue_code LIKE CONCAT('%', #{keyword}, '%'))",
            "</if>",
            "ORDER BY e.create_time DESC, e.id DESC",
            "LIMIT #{offset}, #{limit}",
            "</script>"
    })
    List<KnowledgeRouteTraceRow> selectKnowledgeRouteTraceRows(
            @Param("keyword") String keyword,
            @Param("knowledgeBaseMode") Integer knowledgeBaseMode,
            @Param("status") Integer status,
            @Param("offset") long offset,
            @Param("limit") long limit);
}
