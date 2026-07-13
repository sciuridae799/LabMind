package com.labmind.common.web.config;

import com.labmind.common.web.advice.DefaultExceptionHandler;
import com.labmind.common.web.database.MybatisPlusAutoConfiguration;
import com.labmind.common.web.jackson.JacksonCustom;
import com.labmind.common.web.swagger.SwaggerConfiguration;
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
public class LabMindCommonAutoConfig {
}
