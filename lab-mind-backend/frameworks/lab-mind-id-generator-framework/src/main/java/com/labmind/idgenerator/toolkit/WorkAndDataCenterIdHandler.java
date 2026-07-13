package com.labmind.idgenerator.toolkit;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Objects;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

public class WorkAndDataCenterIdHandler {

    private static final String LUA_SCRIPT_PATH = "lua/workAndDataCenterId.lua";
    private static final String WORK_ID_KEY = "snowflake_work_id";
    private static final String DATA_CENTER_ID_KEY = "snowflake_data_center_id";

    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;
    private final DefaultRedisScript<String> workAndDataCenterIdScript;

    public WorkAndDataCenterIdHandler(StringRedisTemplate stringRedisTemplate) {
        this(stringRedisTemplate, new ObjectMapper());
    }

    WorkAndDataCenterIdHandler(StringRedisTemplate stringRedisTemplate, ObjectMapper objectMapper) {
        this.stringRedisTemplate = Objects.requireNonNull(stringRedisTemplate, "stringRedisTemplate must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.workAndDataCenterIdScript = loadScript();
    }

    public WorkDataCenterId allocate() {
        String allocatedValue;
        try {
            allocatedValue = stringRedisTemplate.execute(
                    workAndDataCenterIdScript,
                    List.of(WORK_ID_KEY, DATA_CENTER_ID_KEY),
                    String.valueOf(WorkDataCenterId.MAX_VALUE));
        }
        catch (RuntimeException ex) {
            throw new IllegalStateException("Failed to allocate snowflake workId and dataCenterId from Redis.", ex);
        }

        if (allocatedValue == null || allocatedValue.isBlank()) {
            throw new IllegalStateException("Redis Lua script returned an empty snowflake workId/dataCenterId payload.");
        }
        return parse(allocatedValue);
    }

    private DefaultRedisScript<String> loadScript() {
        ClassPathResource scriptResource = new ClassPathResource(LUA_SCRIPT_PATH);
        if (!scriptResource.exists()) {
            throw new IllegalStateException("Snowflake Lua script not found: " + LUA_SCRIPT_PATH);
        }

        DefaultRedisScript<String> script = new DefaultRedisScript<>();
        script.setLocation(scriptResource);
        script.setResultType(String.class);
        return script;
    }

    private WorkDataCenterId parse(String allocatedValue) {
        try {
            JsonNode rootNode = objectMapper.readTree(allocatedValue);
            JsonNode workIdNode = rootNode.get("workId");
            JsonNode dataCenterIdNode = rootNode.get("dataCenterId");
            if (workIdNode == null || dataCenterIdNode == null) {
                throw new IllegalStateException("Snowflake allocation payload must contain workId and dataCenterId.");
            }
            if (!workIdNode.isIntegralNumber() || !dataCenterIdNode.isIntegralNumber()) {
                throw new IllegalStateException("Snowflake allocation payload must use integer workId and dataCenterId.");
            }
            return new WorkDataCenterId(workIdNode.longValue(), dataCenterIdNode.longValue());
        }
        catch (JsonProcessingException ex) {
            throw new IllegalStateException(
                    "Failed to parse snowflake workId/dataCenterId response: " + allocatedValue, ex);
        }
        catch (IllegalArgumentException ex) {
            throw new IllegalStateException(
                    "Snowflake allocation payload contains out-of-range workId/dataCenterId: " + allocatedValue, ex);
        }
    }
}
