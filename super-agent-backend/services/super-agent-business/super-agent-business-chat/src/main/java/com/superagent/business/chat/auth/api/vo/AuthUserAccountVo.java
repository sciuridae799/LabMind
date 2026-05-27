package com.superagent.business.chat.auth.api.vo;

import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

@Data
public class AuthUserAccountVo {

    private String userId;

    private String account;

    private String displayName;

    private String role;

    private String workspaceId;

    private String workspaceName;

    private List<AuthWorkspaceVo> workspaces;

    private Boolean enabled;

    private LocalDateTime createTime;
}
