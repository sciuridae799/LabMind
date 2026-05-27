package com.superagent.business.chat.auth.data;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.superagent.common.web.database.BaseTableData;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@TableName("super_agent_auth_session")
public class AuthSessionData extends BaseTableData {

    @TableId(type = IdType.INPUT)
    private Long id;

    private String token;

    private Long userId;

    private String account;

    private String displayName;

    private String role;

    private String workspaceId;

    private LocalDateTime expireTime;

    private Integer status;
}
