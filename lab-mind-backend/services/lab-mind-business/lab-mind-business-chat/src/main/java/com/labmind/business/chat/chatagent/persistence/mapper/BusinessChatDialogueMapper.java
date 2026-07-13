package com.labmind.business.chat.chatagent.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.labmind.business.chat.chatagent.persistence.data.BusinessChatDialogueData;
import com.labmind.business.chat.chatagent.persistence.model.BusinessChatSessionListRow;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 会话主表访问接口。
 *
 * <p>dialogue 代表会话维度，exchange 代表问答轮次。
 * 本 Mapper 中的列表查询会把每个会话和它最新一轮 exchange 关联起来，用于聊天页左侧历史列表。</p>
 */
@Mapper
public interface BusinessChatDialogueMapper extends BaseMapper<BusinessChatDialogueData> {

    /**
     * 统计符合筛选条件的会话数量。
     *
     * <p>筛选条件作用在会话主表和最新一轮 exchange 上：
     * keyword 匹配 conversationId 或最新问题，chatModeCode 匹配会话模式，turnStatusCode 匹配最新轮次状态。</p>
     *
     * @param keyword 关键词，可为空
     * @param chatModeCode 会话模式数据库编码，可为空表示全部模式
     * @param turnStatusCode 最新轮次状态数据库编码，可为空表示全部状态
     * @param status 数据有效状态
     * @return 会话数量
     */
    @Select({
            "<script>",
            "SELECT COUNT(1)",
            "FROM lab_mind_chat_dialogue d",
            "INNER JOIN lab_mind_chat_exchange le",
            "ON le.id = (",
            "    SELECT e.id",
            "    FROM lab_mind_chat_exchange e",
            "    WHERE e.dialogue_code = d.dialogue_code",
            "      AND e.workspace_id = #{workspaceId}",
            "      AND e.status = #{status}",
            "    ORDER BY e.create_time DESC, e.id DESC",
            "    LIMIT 1",
            ")",
            "AND le.status = #{status}",
            "WHERE d.status = #{status}",
            "  AND d.workspace_id = #{workspaceId}",
            "  AND d.auth_session_token = #{authSessionToken}",
            "  AND le.workspace_id = #{workspaceId}",
            "<if test=\"keyword != null and keyword != ''\">",
            "  AND (d.dialogue_code LIKE CONCAT('%', #{keyword}, '%')",
            "       OR d.dialogue_title LIKE CONCAT('%', #{keyword}, '%')",
            "       OR le.user_prompt LIKE CONCAT('%', #{keyword}, '%'))",
            "</if>",
            "<if test=\"chatModeCode != null\">",
            "  AND d.chat_mode = #{chatModeCode}",
            "</if>",
            "<if test=\"turnStatusCode != null\">",
            "  AND le.exchange_state = #{turnStatusCode}",
            "</if>",
            "</script>"
    })
    long countSessionPageRows(
            @Param("workspaceId") String workspaceId,
            @Param("authSessionToken") String authSessionToken,
            @Param("keyword") String keyword,
            @Param("chatModeCode") Integer chatModeCode,
            @Param("turnStatusCode") Integer turnStatusCode,
            @Param("status") Integer status);

    /**
     * 分页查询会话历史列表行。
     *
     * <p>每条返回行只代表一个 conversationId，并携带该会话最新一轮 exchange 的问题、回答、状态和更新时间。
     * 前端点击会话后会再走详情接口读取完整 exchange 列表。</p>
     *
     * @param keyword 关键词，可为空
     * @param chatModeCode 会话模式数据库编码，可为空表示全部模式
     * @param turnStatusCode 最新轮次状态数据库编码，可为空表示全部状态
     * @param status 数据有效状态
     * @param offset 分页偏移量
     * @param limit 分页大小
     * @return 会话列表展示行
     */
    @Select({
            "<script>",
            "SELECT",
            "    d.dialogue_code AS conversationId,",
            "    d.dialogue_title AS title,",
            "    d.chat_mode AS chatModeCode,",
            "    le.exchange_state AS turnStatusCode,",
            "    le.id AS lastExchangeId,",
            "    le.user_prompt AS lastQuestion,",
            "    le.reply_content AS lastReply,",
            "    d.edit_time AS updateTime",
            "FROM lab_mind_chat_dialogue d",
            "INNER JOIN lab_mind_chat_exchange le",
            "ON le.id = (",
            "    SELECT e.id",
            "    FROM lab_mind_chat_exchange e",
            "    WHERE e.dialogue_code = d.dialogue_code",
            "      AND e.workspace_id = #{workspaceId}",
            "      AND e.status = #{status}",
            "    ORDER BY e.create_time DESC, e.id DESC",
            "    LIMIT 1",
            ")",
            "AND le.status = #{status}",
            "WHERE d.status = #{status}",
            "  AND d.workspace_id = #{workspaceId}",
            "  AND d.auth_session_token = #{authSessionToken}",
            "  AND le.workspace_id = #{workspaceId}",
            "<if test=\"keyword != null and keyword != ''\">",
            "  AND (d.dialogue_code LIKE CONCAT('%', #{keyword}, '%')",
            "       OR d.dialogue_title LIKE CONCAT('%', #{keyword}, '%')",
            "       OR le.user_prompt LIKE CONCAT('%', #{keyword}, '%'))",
            "</if>",
            "<if test=\"chatModeCode != null\">",
            "  AND d.chat_mode = #{chatModeCode}",
            "</if>",
            "<if test=\"turnStatusCode != null\">",
            "  AND le.exchange_state = #{turnStatusCode}",
            "</if>",
            "ORDER BY d.edit_time DESC, d.id DESC",
            "LIMIT #{offset}, #{limit}",
            "</script>"
    })
    List<BusinessChatSessionListRow> selectSessionPageRows(
            @Param("workspaceId") String workspaceId,
            @Param("authSessionToken") String authSessionToken,
            @Param("keyword") String keyword,
            @Param("chatModeCode") Integer chatModeCode,
            @Param("turnStatusCode") Integer turnStatusCode,
            @Param("status") Integer status,
            @Param("offset") long offset,
            @Param("limit") long limit);
}
