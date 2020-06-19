package io.github.qsolutionsde.jfhem.amqp;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.qsolutionsde.jfhem.App;
import io.github.qsolutionsde.jfhem.FHEMEventListener;
import io.github.qsolutionsde.jfhem.telnet.FHEMTelnetConnection;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Conditional;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Slf4j
public class AMQPEventPublisher implements FHEMEventListener {
    protected final RabbitTemplate rabbit;

    protected final Map<String,String> prefix = new HashMap<>();

    public AMQPEventPublisher(RabbitTemplate rabbit,
                              List<FHEMTelnetConnection> cs) {
        this.rabbit = rabbit;
        for (FHEMTelnetConnection c :  cs) {
            prefix.put(c.getHost().getHost(),topic("fhem", c.getHost().getHost().replace('.','-')));
            c.addListener(this);
        }
    }

    protected static String topic(String... s) {
        return String.join(".",s);
    }

    protected static final String STATE = "state";

    private final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneOffset.UTC);

    private final ObjectMapper mapper = new ObjectMapper();

    private final MessagePostProcessor jsonPP = message -> {
        message.getMessageProperties().setContentType("application/json");
        return message;
    };

    @Override
    public void event(String host, String deviceType, String device, String reading, String value) {
        String t =  STATE.equals(reading) ?
                topic(prefix.get(host), deviceType, device) :
                topic(prefix.get(host),deviceType, device, reading);
        rabbit.convertAndSend(t,value);

        ObjectNode n = mapper.createObjectNode();

        n.put("value",value);
        n.put("lastUpdate",fmt.format(Instant.now()));

        try {
            rabbit.convertAndSend(topic("timestamped",t), (Object) mapper.writeValueAsString(n),jsonPP);
        } catch (JsonProcessingException e) {
            log.error("Error publishing timestamped event",e);
        }
    }
}
