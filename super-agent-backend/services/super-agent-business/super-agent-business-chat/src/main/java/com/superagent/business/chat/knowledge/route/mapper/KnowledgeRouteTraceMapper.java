package com.superagent.business.chat.knowledge.route.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.superagent.business.chat.knowledge.route.data.KnowledgeRouteTraceData;
import com.superagent.business.chat.knowledge.route.model.KnowledgeRouteTraceRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface KnowledgeRouteTraceMapper extends BaseMapper<KnowledgeRouteTraceData> {

    @Select({
            "<script>",
            "SELECT COUNT(1)",
            "FROM super_agent_knowledge_route_trace",
            "WHERE status = #{status}",
            "<if test=\"keyword != null and keyword != ''\">",
            "  AND (question LIKE CONCAT('%', #{keyword}, '%')",
            "       OR conversation_id LIKE CONCAT('%', #{keyword}, '%'))",
            "</if>",
            "</script>"
    })
    long countTraceRows(@Param("keyword") String keyword, @Param("status") Integer status);

    @Select({
            "<script>",
            "SELECT",
            "  conversation_id AS conversationId,",
            "  exchange_id AS exchangeId,",
            "  question AS question,",
            "  rewritten_question AS rewrittenQuestion,",
            "  route_result_json AS routeResultJson,",
            "  route_mode AS routeMode,",
            "  route_status AS routeStatus,",
            "  confidence AS confidence,",
            "  hit_selected_document AS hitSelectedDocument,",
            "  create_time AS createTime",
            "FROM super_agent_knowledge_route_trace",
            "WHERE status = #{status}",
            "<if test=\"keyword != null and keyword != ''\">",
            "  AND (question LIKE CONCAT('%', #{keyword}, '%')",
            "       OR conversation_id LIKE CONCAT('%', #{keyword}, '%'))",
            "</if>",
            "ORDER BY create_time DESC, id DESC",
            "LIMIT #{offset}, #{limit}",
            "</script>"
    })
    List<KnowledgeRouteTraceRow> selectTraceRows(
            @Param("keyword") String keyword,
            @Param("status") Integer status,
            @Param("offset") long offset,
            @Param("limit") long limit);
}
