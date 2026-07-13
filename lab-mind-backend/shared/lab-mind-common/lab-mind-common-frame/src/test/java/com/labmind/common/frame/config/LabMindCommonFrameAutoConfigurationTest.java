package com.labmind.common.frame.config;

import com.labmind.common.frame.util.SpringUtil;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LabMindCommonFrameAutoConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(LabMindCommonFrameAutoConfiguration.class))
            .withBean(TestBean.class, TestBean::new);

    @Test
    void shouldRegisterSpringUtilBeanAndExposeApplicationContext() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(SpringUtil.class);
            assertThat(SpringUtil.getBean(TestBean.class)).isSameAs(context.getBean(TestBean.class));
            assertThat(SpringUtil.containsBean(context.getBeanNamesForType(TestBean.class)[0])).isTrue();
        });

        assertThatThrownBy(SpringUtil::getApplicationContext)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("has not been initialized");
    }

    @Test
    void shouldRegisterAutoConfigurationImport() throws Exception {
        try (var inputStream = getClass().getClassLoader().getResourceAsStream(
                "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")) {
            assertThat(inputStream).isNotNull();
            String imports = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(imports).contains("com.labmind.common.frame.config.LabMindCommonFrameAutoConfiguration");
        }
    }

    static final class TestBean {
    }
}
