package com.labmind.idgenerator.toolkit;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkAndDataCenterIdHandlerTest {

    @Test
    @SuppressWarnings("unchecked")
    void shouldAllocateMachineIdsFromRedisLuaScript() {
        StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
        when(stringRedisTemplate.execute(any(), anyList(), anyString()))
                .thenReturn("{\"workId\":3,\"dataCenterId\":9}");

        WorkAndDataCenterIdHandler handler = new WorkAndDataCenterIdHandler(stringRedisTemplate, new ObjectMapper());
        WorkDataCenterId workDataCenterId = handler.allocate();

        ArgumentCaptor<DefaultRedisScript<String>> scriptCaptor = ArgumentCaptor.forClass(DefaultRedisScript.class);
        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        ArgumentCaptor<String> argCaptor = ArgumentCaptor.forClass(String.class);
        verify(stringRedisTemplate).execute(scriptCaptor.capture(), keysCaptor.capture(), argCaptor.capture());

        assertThat(workDataCenterId).isEqualTo(new WorkDataCenterId(3, 9));
        assertThat(scriptCaptor.getValue().getResultType()).isEqualTo(String.class);
        assertThat(keysCaptor.getValue()).containsExactly("snowflake_work_id", "snowflake_data_center_id");
        assertThat(argCaptor.getValue()).isEqualTo("31");
    }

    @Test
    void shouldRejectEmptyRedisResult() {
        StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
        when(stringRedisTemplate.execute(any(), anyList(), anyString())).thenReturn(" ");

        WorkAndDataCenterIdHandler handler = new WorkAndDataCenterIdHandler(stringRedisTemplate, new ObjectMapper());

        assertThatThrownBy(handler::allocate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("empty");
    }

    @Test
    void shouldRejectInvalidJsonPayload() {
        StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
        when(stringRedisTemplate.execute(any(), anyList(), anyString())).thenReturn("not-json");

        WorkAndDataCenterIdHandler handler = new WorkAndDataCenterIdHandler(stringRedisTemplate, new ObjectMapper());

        assertThatThrownBy(handler::allocate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to parse");
    }

    @Test
    void shouldRejectMissingFieldsInJsonPayload() {
        StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
        when(stringRedisTemplate.execute(any(), anyList(), anyString())).thenReturn("{\"workId\":1}");

        WorkAndDataCenterIdHandler handler = new WorkAndDataCenterIdHandler(stringRedisTemplate, new ObjectMapper());

        assertThatThrownBy(handler::allocate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must contain workId and dataCenterId");
    }

    @Test
    void shouldRejectOutOfRangeValuesReturnedByLuaScript() {
        StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
        when(stringRedisTemplate.execute(any(), anyList(), anyString()))
                .thenReturn("{\"workId\":32,\"dataCenterId\":1}");

        WorkAndDataCenterIdHandler handler = new WorkAndDataCenterIdHandler(stringRedisTemplate, new ObjectMapper());

        assertThatThrownBy(handler::allocate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("out-of-range")
                .hasRootCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldWrapRedisExecutionFailure() {
        StringRedisTemplate stringRedisTemplate = mock(StringRedisTemplate.class);
        when(stringRedisTemplate.execute(any(), anyList(), anyString()))
                .thenThrow(new RuntimeException("redis down"));

        WorkAndDataCenterIdHandler handler = new WorkAndDataCenterIdHandler(stringRedisTemplate, new ObjectMapper());

        assertThatThrownBy(handler::allocate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to allocate")
                .hasRootCauseMessage("redis down");
    }
}
