package com.example.maoliang;

import com.example.maoliang.Entity.Good;
import com.example.maoliang.Service.CatalogRagService;
import com.example.maoliang.Service.GoodService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;

import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "app.rag.mq.enabled=true",
        "app.rag.mq.publish-delay-ms=100"
})
@EnabledIfEnvironmentVariable(named = "KITTY_RABBITMQ_INTEGRATION", matches = "true")
class CatalogRabbitIntegrationTests {
    @Autowired GoodService goods;
    @Autowired JdbcTemplate jdbc;
    @SpyBean CatalogRagService rag;

    @Test
    void productUpdateTravelsThroughOutboxRabbitAndListener() throws Exception {
        String username = "r" + UUID.randomUUID().toString().substring(0, 7);
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(
                    "INSERT INTO MLuser(username,pwd,power,question,answer) VALUES (?,'test',1,'test','test')",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, username);
            return statement;
        }, key);
        int seller = key.getKey().intValue();
        Good product = new Good();
        product.setGoodname("rabbit" + username);
        product.setDescription("队列测试");
        product.setPrice(20);
        product.setPicture("test.jpg");
        product.setState(0);
        product.setNumber(5);
        product.setKind("猫咪主粮");
        product.setSubkind("test");
        product.setOwner(seller);
        product.setCalorie(4);
        product.setCatkind("通用");
        product.setCatage("1-10");
        product.setCatweight("1-12");
        try {
            clearInvocations(rag);
            goods.add(product);
            verify(rag, timeout(10000)).refreshIndex();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (pending(product.getGoodid()) > 0 && System.nanoTime() < deadline) Thread.sleep(20);
            assertEquals(0, pending(product.getGoodid()));
            assertTrue(product.getGoodid() > 0);
        } finally {
            if (product.getGoodid() > 0) {
                jdbc.update("DELETE FROM rag_catalog_outbox WHERE good_id = ?", product.getGoodid());
                jdbc.update("DELETE FROM MLhistorygood WHERE goodid = ?", product.getGoodid());
                jdbc.update("DELETE FROM MLgood WHERE goodid = ?", product.getGoodid());
            }
            jdbc.update("DELETE FROM MLuser WHERE userid = ?", seller);
        }
    }

    private int pending(int goodId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM rag_catalog_outbox WHERE good_id = ?", Integer.class, goodId);
    }
}
