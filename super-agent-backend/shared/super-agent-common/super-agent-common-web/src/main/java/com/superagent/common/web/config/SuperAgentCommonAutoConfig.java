package com.superagent.common.web.config;

import com.superagent.common.web.advice.DefaultExceptionHandler;
import com.superagent.common.web.database.MybatisPlusAutoConfiguration;
import com.superagent.common.web.jackson.JacksonCustom;
import com.superagent.common.web.swagger.SwaggerConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Import;

@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@Import({
        DefaultExceptionHandler.class,
        JacksonCustom.class,
        MybatisPlusAutoConfiguration.class,
        SwaggerConfiguration.class
})
public class SuperAgentCommonAutoConfig {
}
