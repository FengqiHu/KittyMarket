package com.example.maoliang.Service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

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
        jdbc.update("INSERT INTO rag_catalog_outbox(good_id, operation) VALUES (?, ?)", goodId, operation);
    }
}
