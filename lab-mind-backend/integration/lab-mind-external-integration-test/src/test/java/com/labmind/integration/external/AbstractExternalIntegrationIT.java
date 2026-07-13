package com.labmind.integration.external;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        classes = ExternalIntegrationTestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.NONE)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class AbstractExternalIntegrationIT {

    protected static final Duration HTTP_TIMEOUT = Duration.ofSeconds(10);
    protected static final Duration ASYNC_TIMEOUT = Duration.ofSeconds(20);

    @Autowired
    protected ExternalServiceIntegrationProperties properties;

    @Autowired
    protected HttpClient httpClient;

    protected String runId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().replace("-", "");
    }
}
