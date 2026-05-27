package com.superagent.business.chat.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "super-agent.auth.bootstrap")
public class AuthBootstrapProperties {

    private String superAdminAccount;

    private String superAdminPassword;

    private String superAdminDisplayName;

    private String workspaceId;

    private String workspaceName;
}
