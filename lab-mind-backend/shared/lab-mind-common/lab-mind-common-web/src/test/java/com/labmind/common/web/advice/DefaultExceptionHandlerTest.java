package com.labmind.common.web.advice;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.labmind.common.frame.enums.ErrorCode;
import com.labmind.common.frame.exception.LabMindFrameException;
import com.labmind.common.frame.response.ApiResponse;
import com.labmind.common.web.jackson.JacksonCustom;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.AsyncRequestNotUsableException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DefaultExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new JacksonCustom().labMindJacksonCustomizer().customize(builder);
        ObjectMapper objectMapper = builder.build();

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new DefaultExceptionHandler())
                .setValidator(validator)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void shouldHandleBusinessException() throws Exception {
        mockMvc.perform(get("/test/business"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BIZ_001"))
                .andExpect(jsonPath("$.message").value("business failure"));
    }

    @Test
    void shouldHandleValidationException() throws Exception {
        mockMvc.perform(post("/test/echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "",
                                  "createdAt": "2026-04-20 10:20:30",
                                  "occurredAt": "2026-04-20T02:20:30Z"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"))
                .andExpect(jsonPath("$.message").value("name: must not be blank"));
    }

    @Test
    void shouldHandleUnreadableRequestBody() throws Exception {
        mockMvc.perform(post("/test/echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "demo",
                                  "createdAt": "2026-04-20 10:20:30",
                                  "occurredAt": "2026-04-20T02:20:30Z",
                                  "extra": true
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("400"))
                .andExpect(jsonPath("$.message").value("extra: unknown field"));
    }

    @Test
    void shouldHandleSystemException() throws Exception {
        mockMvc.perform(get("/test/system"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("500"))
                .andExpect(jsonPath("$.message").value("system error"));
    }

    @Test
    void shouldHandleUnsupportedRequestMethod() throws Exception {
        mockMvc.perform(post("/test/business"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("405"))
                .andExpect(jsonPath("$.message").value("method not allowed"));
    }

    @Test
    void shouldHandleMissingResource() throws Exception {
        mockMvc.perform(get("/test/missing-resource"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("404"))
                .andExpect(jsonPath("$.message").value("resource not found"));
    }

    @Test
    void shouldHandleDisconnectedAsyncRequest() throws Exception {
        mockMvc.perform(get("/test/disconnected"))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldUseConfiguredJacksonFormatsForSuccessResponse() throws Exception {
        mockMvc.perform(post("/test/echo")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "demo",
                                  "createdAt": "2026-04-20 10:20:30",
                                  "occurredAt": "2026-04-20T02:20:30Z"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.createdAt").value("2026-04-20 10:20:30"))
                .andExpect(jsonPath("$.data.occurredAt").value("2026-04-20T02:20:30Z"));
    }

    @RestController
    static class TestController {

        @GetMapping("/test/business")
        public ApiResponse<Void> business() {
            throw new LabMindFrameException(TestCode.BIZ_ERROR, "business failure");
        }

        @GetMapping("/test/system")
        public ApiResponse<Void> system() {
            throw new IllegalStateException("boom");
        }

        @GetMapping("/test/disconnected")
        public ApiResponse<Void> disconnected() throws AsyncRequestNotUsableException {
            throw new AsyncRequestNotUsableException("Servlet container error notification for disconnected client");
        }

        @GetMapping("/test/missing-resource")
        public ApiResponse<Void> missingResource() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "/missing-resource");
        }

        @PostMapping("/test/echo")
        public ApiResponse<TestResponse> echo(@Valid @RequestBody TestRequest request) {
            return ApiResponse.ok(new TestResponse(request.getName(), request.getCreatedAt(), request.getOccurredAt()));
        }
    }

    static class TestRequest {

        @NotBlank
        private String name;

        @NotNull
        private LocalDateTime createdAt;

        @NotNull
        private Instant occurredAt;

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }

        public Instant getOccurredAt() {
            return occurredAt;
        }

        public void setOccurredAt(Instant occurredAt) {
            this.occurredAt = occurredAt;
        }
    }

    static class TestResponse {

        private final String name;

        private final LocalDateTime createdAt;

        private final Instant occurredAt;

        TestResponse(String name, LocalDateTime createdAt, Instant occurredAt) {
            this.name = name;
            this.createdAt = createdAt;
            this.occurredAt = occurredAt;
        }

        public String getName() {
            return name;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public Instant getOccurredAt() {
            return occurredAt;
        }
    }

    enum TestCode implements ErrorCode {
        BIZ_ERROR("BIZ_001", "business failure");

        private final String code;

        private final String message;

        TestCode(String code, String message) {
            this.code = code;
            this.message = message;
        }

        @Override
        public String getCode() {
            return code;
        }

        @Override
        public String getMessage() {
            return message;
        }
    }
}
