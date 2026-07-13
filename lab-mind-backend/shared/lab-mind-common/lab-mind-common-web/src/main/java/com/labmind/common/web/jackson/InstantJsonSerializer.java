package com.labmind.common.web.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

public class InstantJsonSerializer extends JsonSerializer<Instant> {

    @Override
    public void serialize(Instant value, JsonGenerator generator, SerializerProvider serializers) throws IOException {
        generator.writeString(DateTimeFormatter.ISO_INSTANT.format(value));
    }
}
