package com.labmind.business.chat.chatagent.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class BusinessChatExecutionConfiguration {

    @Bean(name = "businessChatToolExecutor", destroyMethod = "shutdown")
    public ExecutorService businessChatToolExecutor(BusinessChatRuntimeProperties runtimeProperties) {
        return Executors.newFixedThreadPool(runtimeProperties.getMaxParallelTools());
    }
}
