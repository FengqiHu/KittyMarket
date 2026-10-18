package com.example.maoliang;

import com.example.maoliang.Entity.Good;
import com.example.maoliang.Service.GoodService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "app.rag.mq.enabled=true",
        "app.rag.mq.publish-delay-ms=600000",
        "spring.rabbitmq.listener.simple.auto-startup=false"
})
class CatalogOutboxIntegrationTests {
    @Autowired GoodService goods;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transaction;

    @Test
    void productChangesAndOutboxCommitOrRollBackTogether() {
        String username = "q" + UUID.randomUUID().toString().substring(0, 7);
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
        product.setGoodname("mq" + username);
        product.setDescription("原始描述");
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
            goods.add(product);
            int id = product.getGoodid();
            assertTrue(id > 0);
            assertEquals(List.of("CREATED"), events(id));

            transaction.executeWithoutResult(status -> {
                Good changed = goods.showGood(id);
                changed.setDescription("不应保留");
                assertTrue(goods.updateGood(changed));
                status.setRollbackOnly();
            });
            assertEquals("原始描述", goods.showGood(id).getDescription());
            assertEquals(List.of("CREATED"), events(id));

            Good changed = goods.showGood(id);
            changed.setDescription("已更新");
            assertTrue(goods.updateGood(changed));
            assertEquals(1, goods.remove(id));
            assertEquals(List.of("CREATED", "UPDATED", "DELETED"), events(id));
        } finally {
            if (product.getGoodid() > 0) {
                jdbc.update("DELETE FROM rag_catalog_outbox WHERE good_id = ?", product.getGoodid());
                jdbc.update("DELETE FROM MLhistorygood WHERE goodid = ?", product.getGoodid());
                jdbc.update("DELETE FROM MLgood WHERE goodid = ?", product.getGoodid());
            }
            jdbc.update("DELETE FROM MLuser WHERE userid = ?", seller);
        }
    }

    private List<String> events(int goodId) {
        return jdbc.queryForList("SELECT operation FROM rag_catalog_outbox WHERE good_id = ? ORDER BY id",
                String.class, goodId);
    }
}
