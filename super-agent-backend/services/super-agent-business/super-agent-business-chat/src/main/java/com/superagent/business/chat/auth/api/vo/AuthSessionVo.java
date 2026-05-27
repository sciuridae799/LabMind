package com.superagent.business.chat.auth.api.vo;

import java.util.List;
import lombok.Data;

@Data
public class AuthSessionVo {

    private String token;

    private String userId;

    private String account;

    private String displayName;

    private String role;

    private String workspaceId;

    private String workspaceName;

    private List<AuthWorkspaceVo> accessibleWorkspaces;
}
