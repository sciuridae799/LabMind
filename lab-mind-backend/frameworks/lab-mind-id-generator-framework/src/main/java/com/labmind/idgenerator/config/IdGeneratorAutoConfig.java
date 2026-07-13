package com.labmind.idgenerator.config;

import com.labmind.idgenerator.toolkit.SnowflakeIdGenerator;
import com.labmind.idgenerator.toolkit.WorkAndDataCenterIdHandler;
import com.labmind.idgenerator.toolkit.WorkDataCenterId;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.StringRedisTemplate;

@AutoConfiguration(after = RedisAutoConfiguration.class)
public class IdGeneratorAutoConfig {

    @Bean
    public WorkAndDataCenterIdHandler workAndDataCenterIdHandler(StringRedisTemplate stringRedisTemplate) {
        return new WorkAndDataCenterIdHandler(stringRedisTemplate);
    }

    @Bean
    public WorkDataCenterId workDataCenterId(WorkAndDataCenterIdHandler workAndDataCenterIdHandler) {
        return workAndDataCenterIdHandler.allocate();
    }

    @Bean
    public SnowflakeIdGenerator snowflakeIdGenerator(WorkDataCenterId workDataCenterId) {
        return new SnowflakeIdGenerator(workDataCenterId.workId(), workDataCenterId.dataCenterId());
    }
}
