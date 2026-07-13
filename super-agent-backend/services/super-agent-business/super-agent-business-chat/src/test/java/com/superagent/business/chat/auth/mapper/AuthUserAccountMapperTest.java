package com.superagent.business.chat.auth.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

class AuthUserAccountMapperTest {

    @Test
    void shouldUseWriteLocksForSuperAdminInvariantQueries() throws Exception {
        assertForUpdateQuery(
                "selectAvailableSuperAdminIdsForUpdate",
                String.class,
                Integer.class,
                Integer.class);
        assertForUpdateQuery("selectByAccountForUpdate", String.class);
    }

    private void assertForUpdateQuery(String methodName, Class<?>... parameterTypes) throws Exception {
        Method mapperMethod = AuthUserAccountMapper.class.getMethod(methodName, parameterTypes);
        Select select = mapperMethod.getAnnotation(Select.class);

        assertThat(select).isNotNull();
        assertThat(String.join(" ", select.value())).containsIgnoringCase("FOR UPDATE");
    }
}
