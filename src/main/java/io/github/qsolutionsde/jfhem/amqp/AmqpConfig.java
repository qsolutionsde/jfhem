package io.github.qsolutionsde.jfhem.amqp;

import lombok.Data;

@Data
public class AmqpConfig {
    protected String queue = "fhem-commandqueue";
    protected String exchange = "amq.topic";
    protected String prefix = "fhem";
    protected String timestampPrefix = "timestamped.fhem";
    protected int ttl = 3600000;
}
