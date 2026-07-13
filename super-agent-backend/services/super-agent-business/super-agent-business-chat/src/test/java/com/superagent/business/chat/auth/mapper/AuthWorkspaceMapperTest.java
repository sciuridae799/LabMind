package com.superagent.business.chat.auth.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class AuthWorkspaceMapperTest {

    @Test
    void shouldLockWorkspaceRowsForBootstrapRecovery() throws Exception {
        Method method = AuthWorkspaceMapper.class.getMethod("selectByWorkspaceIdForUpdate", String.class);
        Select select = method.getAnnotation(Select.class);

        assertThat(select).isNotNull();
        assertThat(String.join(" ", select.value()))
                .containsIgnoringCase("WHERE workspace_id = #{workspaceId}")
                .containsIgnoringCase("FOR UPDATE");
    }
}
