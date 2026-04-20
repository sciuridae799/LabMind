package com.superagent.idgenerator.config;

import com.superagent.idgenerator.toolkit.SnowflakeIdGenerator;
import com.superagent.idgenerator.toolkit.WorkAndDataCenterIdHandler;
import com.superagent.idgenerator.toolkit.WorkDataCenterId;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IdGeneratorAutoConfigTest {

    @Test
    void shouldAssembleSnowflakeBeansFromAllocatedMachineIds() {
        StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
        when(stringRedisTemplate.execute(any(), anyList(), anyString()))
                .thenReturn("{\"workId\":6,\"dataCenterId\":8}");

        IdGeneratorAutoConfig autoConfig = new IdGeneratorAutoConfig();
        WorkAndDataCenterIdHandler handler = autoConfig.workAndDataCenterIdHandler(stringRedisTemplate);
        WorkDataCenterId workDataCenterId = autoConfig.workDataCenterId(handler);
        SnowflakeIdGenerator snowflakeIdGenerator = autoConfig.snowflakeIdGenerator(workDataCenterId);

        assertThat(workDataCenterId).isEqualTo(new WorkDataCenterId(6, 8));
        assertThat(snowflakeIdGenerator.parseIdTimestamp(snowflakeIdGenerator.nextId()))
                .isGreaterThan(0L);
    }

    @Test
    void shouldRegisterAutoConfigurationImport() throws Exception {
        try (var inputStream = getClass().getClassLoader().getResourceAsStream(
                "META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports")) {
            assertThat(inputStream).isNotNull();
            String imports = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(imports).contains("com.superagent.idgenerator.config.IdGeneratorAutoConfig");
        }
    }
}
