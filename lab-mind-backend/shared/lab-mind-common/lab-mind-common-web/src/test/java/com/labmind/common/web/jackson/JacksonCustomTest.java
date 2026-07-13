package com.labmind.common.web.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JacksonCustomTest {

    @Test
    void shouldSerializeConfiguredDateTypes() throws Exception {
        ObjectMapper objectMapper = createObjectMapper();
        Date legacyDate = Date.from(LocalDateTime.of(2026, 4, 20, 10, 20, 30)
                .atZone(ZoneId.systemDefault())
                .toInstant());
        TestPayload payload = new TestPayload(
                LocalDateTime.of(2026, 4, 20, 10, 20, 30),
                LocalDate.of(2026, 4, 20),
                LocalTime.of(10, 20, 30),
                Instant.parse("2026-04-20T02:20:30Z"),
                legacyDate);

        String json = objectMapper.writeValueAsString(payload);

        assertThat(json).contains("\"localDateTime\":\"2026-04-20 10:20:30\"");
        assertThat(json).contains("\"localDate\":\"2026-04-20\"");
        assertThat(json).contains("\"localTime\":\"10:20:30\"");
        assertThat(json).contains("\"instant\":\"2026-04-20T02:20:30Z\"");
        assertThat(json).contains("\"date\":\"2026-04-20 10:20:30\"");
    }

    @Test
    void shouldDeserializeConfiguredDateTypes() throws Exception {
        ObjectMapper objectMapper = createObjectMapper();

        TestPayload payload = objectMapper.readValue("""
                {
                  "localDateTime": "2026-04-20 10:20:30",
                  "localDate": "2026-04-20",
                  "localTime": "10:20:30",
                  "instant": "2026-04-20T02:20:30Z",
                  "date": "2026-04-20 10:20:30"
                }
                """, TestPayload.class);

        assertThat(payload.getLocalDateTime()).isEqualTo(LocalDateTime.of(2026, 4, 20, 10, 20, 30));
        assertThat(payload.getLocalDate()).isEqualTo(LocalDate.of(2026, 4, 20));
        assertThat(payload.getLocalTime()).isEqualTo(LocalTime.of(10, 20, 30));
        assertThat(payload.getInstant()).isEqualTo(Instant.parse("2026-04-20T02:20:30Z"));
        assertThat(payload.getDate().toInstant()).isEqualTo(LocalDateTime.of(2026, 4, 20, 10, 20, 30)
                .atZone(ZoneId.systemDefault())
                .toInstant());
    }

    @Test
    void shouldRejectUnknownProperties() {
        ObjectMapper objectMapper = createObjectMapper();

        assertThatThrownBy(() -> objectMapper.readValue("""
                {
                  "localDateTime": "2026-04-20 10:20:30",
                  "localDate": "2026-04-20",
                  "localTime": "10:20:30",
                  "instant": "2026-04-20T02:20:30Z",
                  "date": "2026-04-20 10:20:30",
                  "extra": true
                }
                """, TestPayload.class))
                .isInstanceOf(UnrecognizedPropertyException.class);
    }

    private ObjectMapper createObjectMapper() {
        Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
        new JacksonCustom().labMindJacksonCustomizer().customize(builder);
        return builder.build();
    }

    static class TestPayload {

        private LocalDateTime localDateTime;

        private LocalDate localDate;

        private LocalTime localTime;

        private Instant instant;

        private Date date;

        TestPayload() {
        }

        TestPayload(LocalDateTime localDateTime, LocalDate localDate, LocalTime localTime, Instant instant, Date date) {
            this.localDateTime = localDateTime;
            this.localDate = localDate;
            this.localTime = localTime;
            this.instant = instant;
            this.date = date;
        }

        public LocalDateTime getLocalDateTime() {
            return localDateTime;
        }

        public void setLocalDateTime(LocalDateTime localDateTime) {
            this.localDateTime = localDateTime;
        }

        public LocalDate getLocalDate() {
            return localDate;
        }

        public void setLocalDate(LocalDate localDate) {
            this.localDate = localDate;
        }

        public LocalTime getLocalTime() {
            return localTime;
        }

        public void setLocalTime(LocalTime localTime) {
            this.localTime = localTime;
        }

        public Instant getInstant() {
            return instant;
        }

        public void setInstant(Instant instant) {
            this.instant = instant;
        }

        public Date getDate() {
            return date;
        }

        public void setDate(Date date) {
            this.date = date;
        }
    }
}
