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

/** 声明商品索引更新使用的交换机、工作队列和失败后的死信队列；仅在启用 MQ 时生效。 */
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
        // 消费失败且重试耗尽的消息由工作队列转发到死信队列，供排查或重新投递。
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
