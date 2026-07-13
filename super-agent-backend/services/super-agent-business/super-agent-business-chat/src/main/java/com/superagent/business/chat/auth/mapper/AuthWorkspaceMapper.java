package com.superagent.business.chat.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.superagent.business.chat.auth.data.AuthWorkspaceData;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AuthWorkspaceMapper extends BaseMapper<AuthWorkspaceData> {

    @Select({
            "SELECT",
            "    id,",
            "    workspace_id AS workspaceId,",
            "    workspace_name AS workspaceName,",
            "    create_time AS createTime,",
            "    edit_time AS editTime,",
            "    status",
            "FROM super_agent_workspace",
            "WHERE workspace_id = #{workspaceId}",
            "LIMIT 1",
            "FOR UPDATE"
    })
    AuthWorkspaceData selectByWorkspaceIdForUpdate(@Param("workspaceId") String workspaceId);
}
