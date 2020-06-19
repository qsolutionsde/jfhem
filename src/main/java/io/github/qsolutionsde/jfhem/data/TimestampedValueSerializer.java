package io.github.qsolutionsde.jfhem.data;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.ser.std.StdSerializer;
import lombok.extern.slf4j.Slf4j;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@SuppressWarnings("rawtypes")
@Slf4j
public class TimestampedValueSerializer extends StdSerializer<TimestampedValue> {
    private final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    public TimestampedValueSerializer() {
        this(null);
    }

    public TimestampedValueSerializer(Class<TimestampedValue> t) {
        super(t);
    }

    @Override
    public void serialize(
            TimestampedValue value, JsonGenerator jgen, SerializerProvider provider) {

        try {
            if (value.isValid()) {
                jgen.writeStartObject();
                if (value.value() instanceof Double)
                    jgen.writeNumberField("value", (Double) value.value());
                else if (value.value() instanceof Long)
                    jgen.writeNumberField("value", (Long) value.value());
                else if (value.value() instanceof Boolean)
                    jgen.writeBooleanField("value", (Boolean) value.value());
                else
                    jgen.writeStringField("value", value.value().toString());

                jgen.writeStringField("lastUpdate", fmt.format(value.lastUpdate()));
                jgen.writeEndObject();
            } else
                log.warn("Invalid timestamped value for serialization");
        } catch (Exception e) {
            log.error("Error serializing {}, recovering",value,e);
        }
    }
}
