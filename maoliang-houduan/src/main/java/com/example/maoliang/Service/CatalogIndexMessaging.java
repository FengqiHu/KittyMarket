package com.example.maoliang.Service;

import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.util.Map;

@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.rag.mq.enabled", havingValue = "true")
public class CatalogIndexMessaging {
    public static final String EXCHANGE = "kittymarket.catalog.index.v1";
    public static final String ROUTING_KEY = "catalog.changed";
    public static final String QUEUE = "kittymarket.catalog.index.v1";
    public static final String DEAD_LETTER_EXCHANGE = "kittymarket.catalog.index.dlx.v1";
    public static final String DEAD_LETTER_QUEUE = "kittymarket.catalog.index.dead.v1";

    @Bean
    public Declarables catalogIndexQueues() {
        DirectExchange exchange = new DirectExchange(EXCHANGE, true, false);
        DirectExchange deadLetterExchange = new DirectExchange(DEAD_LETTER_EXCHANGE, true, false);
        Queue queue = new Queue(QUEUE, true, false, false,
                Map.of("x-dead-letter-exchange", DEAD_LETTER_EXCHANGE,
                        "x-dead-letter-routing-key", ROUTING_KEY));
        Queue deadLetterQueue = new Queue(DEAD_LETTER_QUEUE, true);
        return new Declarables(exchange, deadLetterExchange, queue, deadLetterQueue,
                BindingBuilder.bind(queue).to(exchange).with(ROUTING_KEY),
                BindingBuilder.bind(deadLetterQueue).to(deadLetterExchange).with(ROUTING_KEY));
    }
}
