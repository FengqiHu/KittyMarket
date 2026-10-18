package com.example.maoliang.Service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** 消费商品变更通知，并按数据库最新商品状态刷新本进程的 RAG 索引。 */
@Component
@ConditionalOnProperty(name = "app.rag.mq.enabled", havingValue = "true")
public class CatalogIndexListener {
    private static final Logger LOGGER = LoggerFactory.getLogger(CatalogIndexListener.class);
    private final CatalogRagService rag;

    public CatalogIndexListener(CatalogRagService rag) {
        this.rag = rag;
    }

    @RabbitListener(queues = CatalogIndexMessaging.QUEUE)
    public void refresh(String eventId) {
        // 消息中的 ID 只用于标识通知；刷新时读取当前目录，不依赖消息内的商品快照。
        long id = Long.parseLong(eventId);
        if (id < 1) throw new IllegalArgumentException("Invalid catalog event ID");
        rag.refreshIndex();
        LOGGER.info("Refreshed catalog RAG index after event {}", id);
    }
}
