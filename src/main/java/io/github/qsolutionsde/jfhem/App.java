package io.github.qsolutionsde.jfhem;

import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.github.qsolutionsde.jfhem.amqp.AMQPEventController;
import io.github.qsolutionsde.jfhem.amqp.AMQPEventPublisher;
import io.github.qsolutionsde.jfhem.amqp.AmqpConfig;
import io.github.qsolutionsde.jfhem.data.TimestampedValueModule;
import io.github.qsolutionsde.jfhem.http.FHEMHttpConnection;
import io.github.qsolutionsde.jfhem.http.FHEMWebConfig;
import io.github.qsolutionsde.jfhem.telnet.FHEMTelnetCommandExecutor;
import io.github.qsolutionsde.jfhem.telnet.FHEMTelnetConfig;
import io.github.qsolutionsde.jfhem.telnet.FHEMTelnetConnection;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.scheduling.annotation.EnableAsync;

import java.net.http.HttpClient;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;

@SpringBootApplication
@Slf4j
@EnableRetry
@EnableAsync
@ConfigurationProperties("fhemgateway")
public class App {

    @Getter @Setter protected FHEMTelnetConfig telnet;
    @Getter @Setter protected FHEMWebConfig http;

    public static void main(String[] args) {
        SpringApplication.run(App.class, args);
    }

    @Bean SimpleModule timestampedModule() {
        return new TimestampedValueModule();
    }

    @Bean JavaTimeModule javaTimeModule() {
        return new JavaTimeModule();
    }

    @Bean
    List<FHEMTelnetConnection> telnetControllers() {
        if (telnet == null)
            return Collections.emptyList();
        log.info("Starting telnet connections");
        return telnet.getHosts().stream()
                .map(FHEMTelnetConnection::new)
                .collect(Collectors.toList());
    }

    @Bean("executors")
    List<FHEMTelnetCommandExecutor> telnetExecutors() {
        if (telnet == null)
            return Collections.emptyList();
        log.info("Starting telnet executors");
        return telnet.getHosts().stream()
                .map(FHEMTelnetCommandExecutor::new)
                .collect(Collectors.toList());
    }

    @Bean
    List<FHEMHttpConnection> webConnections() {
        if (http == null)
            return Collections.emptyList();
        log.info("Starting http connections");
        return http.getHosts().stream()
                .map(FHEMHttpConnection::new)
                .collect(Collectors.toList());
    }

    @Getter @Setter protected AmqpConfig amqp;

    @Bean public Queue fhemCommandQueue() {
        return QueueBuilder.durable(amqp.getQueue()).ttl(amqp.getTtl()).build();
    }

    @Bean
    public Declarables fhemCommandQueueBindings(Queue q) {
        final String p = amqp.getPrefix() + ".*.";
        log.info("Declaring fhem queue");
        return new Declarables(createDeviceQueue(
                amqp.getExchange(),
                q,
                new String[] { p + "set.#", p + "setreading.#", p + "get.#",  p + "shutdown.#", p + "update" },
                amqp.getTtl()));
    }

    @Bean
    public AMQPEventController amqpEventController( RabbitTemplate template,
                                                    @Qualifier("executors") List<? extends FHEMCommandExecutor> l,
                                                    Queue q) {
        log.info("Creating AMQP event controller");
        return new AMQPEventController(template,l,q);
    }

    @Bean
    public AMQPEventPublisher amqpEventPublisher(RabbitTemplate rabbit,
                                                 @Qualifier("telnetControllers") List<FHEMTelnetConnection> cs) {
        log.info("Creating AMQP event publisher");
        return new AMQPEventPublisher(rabbit,cs);
    }

    protected List<Declarable> createDeviceQueue(String exchange, Queue q, String[] keys, int ttl) {
        final TopicExchange te = ExchangeBuilder.topicExchange(exchange).build();
        List<Declarable> l = new LinkedList<>();
        l.add(q);
        for (String k : keys)
            l.add(BindingBuilder.bind(q).to(te).with(k));
        return l;
    }
}
