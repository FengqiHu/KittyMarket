package com.example.maoliang.Repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Statement;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"app.rag.mq.enabled=false", "app.rag.enabled=false"})
@Transactional
class FavoritesIntegrationTests {
    @Autowired GoodRepository repository;
    @Autowired JdbcTemplate jdbc;
    private int buyer;
    private int other;
    private int good;

    @BeforeEach
    void fixtures() {
        String suffix = UUID.randomUUID().toString().substring(0, 7);
        buyer = insert("INSERT INTO MLuser(username,pwd,power,question,answer) VALUES (?,'test',0,'test','test')", "f" + suffix);
        other = insert("INSERT INTO MLuser(username,pwd,power,question,answer) VALUES (?,'test',0,'test','test')", "g" + suffix);
        good = insert("""
                INSERT INTO MLgood(goodname,description,price,picture,state,number,kind,subkind,owner,calorie,catkind,catage,catweight)
                VALUES ('favorite-test','test',10,'test.jpg',0,10,'猫咪主粮','test',?,4,'通用','0-20','0-20')
                """, other);
    }

    @Test
    void addingAnExistingCartItemOnlyFavoritesItForTheCurrentBuyer() {
        jdbc.update("INSERT INTO MLbuying(goodid,number,islike,buyer) VALUES (?,2,0,?),(?,3,0,?)",
                good, buyer, good, other);
        repository.addtolike(good, buyer);
        repository.addtolike(good, buyer);
        assertEquals(1, repository.showLike(buyer, 1).size());
        assertTrue(repository.showLike(other, 1).isEmpty());
        assertEquals(2, quantity(buyer));
        assertEquals(3, quantity(other));
        repository.cancellike(good, buyer);
        assertTrue(repository.showLike(buyer, 1).isEmpty());
        assertEquals(2, quantity(buyer));
    }

    @Test
    void aNewFavoriteSurvivesReloadWithoutAddingCartQuantity() {
        repository.addtolike(good, buyer);
        assertEquals(1, repository.showLike(buyer, 1).size());
        assertEquals(0, quantity(buyer));
        assertTrue(repository.showBuyerCart(buyer).isEmpty());
        repository.cancellike(good, buyer);
        assertTrue(repository.showLike(buyer, 1).isEmpty());
    }

    private int quantity(int userId) {
        return jdbc.queryForObject("SELECT number FROM MLbuying WHERE goodid = ? AND buyer = ?", Integer.class, good, userId);
    }

    private int insert(String sql, Object... values) {
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            return statement;
        }, key);
        return key.getKey().intValue();
    }
}
