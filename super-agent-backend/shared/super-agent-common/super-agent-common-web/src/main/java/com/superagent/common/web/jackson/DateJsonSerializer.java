package com.superagent.common.web.jackson;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import java.io.IOException;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;

public class DateJsonSerializer extends JsonSerializer<Date> {

    private static final DateTimeFormatter FORMATTER =
            DateTimeFormatter.ofPattern(DateJsonDeserializer.DATE_TIME_PATTERN);

    @Override
    public void serialize(Date value, JsonGenerator generator, SerializerProvider serializers) throws IOException {
        generator.writeString(FORMATTER.format(value.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()));
    }
}
