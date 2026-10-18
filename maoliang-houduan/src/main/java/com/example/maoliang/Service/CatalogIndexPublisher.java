package com.example.maoliang.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.TimeUnit;

/** 定时将已提交的 outbox 记录发布到 RabbitMQ，发送失败时保留记录等待下轮重试。 */
@Component
@ConditionalOnProperty(name = "app.rag.mq.enabled", havingValue = "true")
public class CatalogIndexPublisher {
    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogIndexPublisher.class);
    private final JdbcTemplate jdbc;
    private final RabbitTemplate rabbit;

    public CatalogIndexPublisher(JdbcTemplate jdbc, RabbitTemplate rabbit) {
        this.jdbc = jdbc;
        this.rabbit = rabbit;
    }

    @Scheduled(fixedDelayString = "${app.rag.mq.publish-delay-ms:2000}")
    public void publishPending() {
        List<Long> ids = jdbc.queryForList("SELECT id FROM rag_catalog_outbox ORDER BY id LIMIT 100", Long.class);
        for (Long id : ids) {
            try {
                CorrelationData correlation = new CorrelationData(Long.toString(id));
                rabbit.convertAndSend(CatalogIndexMessaging.EXCHANGE, CatalogIndexMessaging.ROUTING_KEY,
                        Long.toString(id), correlation);
                // 收到 broker 确认且消息成功路由后才能删除；重复投递仍可能发生，消费者需可重复执行。
                CorrelationData.Confirm confirm = correlation.getFuture().get(5, TimeUnit.SECONDS);
                if (!confirm.isAck() || correlation.getReturned() != null) {
                    throw new IllegalStateException("RabbitMQ did not route catalog event " + id);
                }
                jdbc.update("DELETE FROM rag_catalog_outbox WHERE id = ?", id);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception exception) {
                LOGGER.warn("Catalog index event {} remains in outbox for retry: {}", id,
                        exception.getClass().getSimpleName());
                return;
            }
        }
    }
}
