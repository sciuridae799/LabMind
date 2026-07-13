package com.labmind.business.chat.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "lab-mind.auth.bootstrap")
public class AuthBootstrapProperties {

    private String superAdminAccount;

    private String superAdminPassword;

    private String superAdminDisplayName;

    private String workspaceId;

    private String workspaceName;
}
