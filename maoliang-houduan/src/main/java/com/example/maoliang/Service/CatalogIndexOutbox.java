package com.example.maoliang.Service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** 把商品变更记录写入 MySQL outbox；由业务方法的事务保证它与商品变更一起提交。 */
@Component
public class CatalogIndexOutbox {
    private final JdbcTemplate jdbc;
    private final boolean enabled;

    public CatalogIndexOutbox(JdbcTemplate jdbc, @Value("${app.rag.mq.enabled:false}") boolean enabled) {
        this.jdbc = jdbc;
        this.enabled = enabled;
    }

    public void enqueue(int goodId, String operation) {
        if (!enabled) return;
        // 此处不直接连接 RabbitMQ，避免数据库已提交而消息发送失败时丢失更新通知。
        jdbc.update("INSERT INTO rag_catalog_outbox(good_id, operation) VALUES (?, ?)", goodId, operation);
    }
}
