package com.labmind.common.web.jackson;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Date;
import org.springframework.util.StringUtils;

public class DateJsonDeserializer extends JsonDeserializer<Date> {

    public static final String DATE_TIME_PATTERN = JacksonCustom.DATE_TIME_PATTERN;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);

    @Override
    public Date deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        String text = parser.getValueAsString();
        if (!StringUtils.hasText(text)) {
            throw InvalidFormatException.from(parser, "Date text must not be blank.", text, Date.class);
        }
        try {
            LocalDateTime localDateTime = LocalDateTime.parse(text.trim(), FORMATTER);
            return Date.from(localDateTime.atZone(ZoneId.systemDefault()).toInstant());
        }
        catch (DateTimeParseException exception) {
            throw InvalidFormatException.from(
                    parser,
                    "Date text must match pattern " + DATE_TIME_PATTERN + ".",
                    text,
                    Date.class);
        }
    }
}
