package com.superagent.business.chat.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.superagent.business.chat.auth.data.AuthUserAccountData;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface AuthUserAccountMapper extends BaseMapper<AuthUserAccountData> {

    /**
     * Locks every currently available super administrator in a stable order.
     * Callers must hold the transaction until the guarded account mutation completes.
     */
    @Select({
            "SELECT id",
            "FROM super_agent_user_account",
            "WHERE role = #{role}",
            "  AND enabled = #{enabled}",
            "  AND status = #{status}",
            "ORDER BY id",
            "FOR UPDATE"
    })
    List<Long> selectAvailableSuperAdminIdsForUpdate(
            @Param("role") String role,
            @Param("enabled") Integer enabled,
            @Param("status") Integer status);

    /** Locks the configured bootstrap account, including soft-deleted rows. */
    @Select({
            "SELECT",
            "    id,",
            "    account,",
            "    display_name AS displayName,",
            "    password_hash AS passwordHash,",
            "    password_salt AS passwordSalt,",
            "    role,",
            "    workspace_id AS workspaceId,",
            "    enabled,",
            "    create_time AS createTime,",
            "    edit_time AS editTime,",
            "    status",
            "FROM super_agent_user_account",
            "WHERE account = #{account}",
            "LIMIT 1",
            "FOR UPDATE"
    })
    AuthUserAccountData selectByAccountForUpdate(@Param("account") String account);
}
