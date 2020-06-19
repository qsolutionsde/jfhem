package io.github.qsolutionsde.jfhem.data;

import com.fasterxml.jackson.databind.module.SimpleModule;

public class TimestampedValueModule extends SimpleModule {
    public TimestampedValueModule() {
        addSerializer(TimestampedValue.class, new TimestampedValueSerializer());
        addDeserializer(TimestampedValue.class, new TimestampedValueDeserializer());
    }
}
