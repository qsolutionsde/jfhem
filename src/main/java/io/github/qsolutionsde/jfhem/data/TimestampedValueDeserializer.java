package io.github.qsolutionsde.jfhem.data;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.deser.std.StdDeserializer;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@SuppressWarnings("rawtypes")
@Slf4j
public class TimestampedValueDeserializer extends StdDeserializer<TimestampedValue> {

    private final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    public TimestampedValueDeserializer() {
        this(null);
    }

    public TimestampedValueDeserializer(Class<?> vc) {
        super(vc);
    }

    @Override
    public TimestampedValue deserialize(JsonParser jp, DeserializationContext ctxt)
            throws IOException {
        JsonNode node = jp.getCodec().readTree(jp);

        JsonNode value = node.get("value");
        JsonNode instant = node.get("lastUpdate");

        Instant i;

        try {
            i = Instant.from(fmt.parse(instant.asText()));
        }
        catch (DateTimeException e) {

            try {
                long epoch = Long.parseLong(instant.asText());
                i = Instant.ofEpochMilli(epoch);
            } catch (NumberFormatException f) {
                i = Instant.now();
                log.error("Error deserializing timestamp {}",instant.asText(),e);
            }
        }
        if (value.isDouble() || value.isFloat())
            return new TimestampedValue<>(value.asDouble(), i);

        if (value.isBoolean())
            return new TimestampedValue<>(value.asBoolean(),i);

        if (value.isInt() || value.isLong())
            return new TimestampedValue<>(value.asLong(), i);

        return new TimestampedValue<>(value.asText(), i);
    }
}
