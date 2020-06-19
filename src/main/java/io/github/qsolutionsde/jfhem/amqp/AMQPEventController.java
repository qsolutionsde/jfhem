package io.github.qsolutionsde.jfhem.amqp;

import io.github.qsolutionsde.jfhem.FHEMCommandExecutor;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.scheduling.annotation.Async;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class AMQPEventController {

    protected final Map<String, FHEMCommandExecutor> executors = new HashMap<>();

    protected final RabbitTemplate rabbit;

    @Getter
    protected final Queue queue;

    public AMQPEventController(RabbitTemplate template, List<? extends FHEMCommandExecutor> l, Queue q) {
        this.rabbit = template;
        this.queue = q;
        l.forEach(e -> executors.put(e.getHost(),e));
    }

    @RabbitListener(queues = "${fhemgateway.amqp.queue}", concurrency = "1")
    public void processMessageMain(@Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String topic,
                                   @Payload String payload) {
        String[] t = topic.split("\\.");

        if (t.length == 3)
            fhemExec(t[1], t[2],topic(t[0],t[1],"result",t[2]));
        else
            fhemExec(t[1],t[2] + " " + t[t.length - 1] + " " + payload,topic(t[0],t[1],"result",t[2],t[t.length-1]));
    }

    protected static String topic(String... s) {
        return String.join(".",s);
    }

    @Async
    protected void fhemExec(String host, String cmd,String topic) {
        if (executors.containsKey(host)) {
            String line = executors.get(host).execute(cmd);
            if (line != null)
                rabbit.convertAndSend(topic, line);
        } else
            log.warn("No executor for {}", host);
    }
}
