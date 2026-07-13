package com.labmind.common.web.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import org.springframework.util.StringUtils;

public class InstantJsonDeserializer extends JsonDeserializer<Instant> {

    @Override
    public Instant deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        String text = parser.getValueAsString();
        if (!StringUtils.hasText(text)) {
            throw InvalidFormatException.from(parser, "Instant text must not be blank.", text, Instant.class);
        }
        try {
            return Instant.parse(text.trim());
        }
        catch (DateTimeParseException exception) {
            throw InvalidFormatException.from(
                    parser,
                    "Instant text must be an ISO-8601 instant.",
                    text,
                    Instant.class);
        }
    }
}
