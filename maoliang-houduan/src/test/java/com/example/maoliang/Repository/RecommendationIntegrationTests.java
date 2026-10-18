package com.example.maoliang.Repository;

import com.example.maoliang.Entity.Usr;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Statement;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"app.rag.mq.enabled=false", "app.rag.enabled=false"})
@Transactional
class RecommendationIntegrationTests {
    @Autowired RecommendationRepository recommendations;
    @Autowired JdbcTemplate jdbc;
    private Usr buyer;
    private Usr other;
    private String category;

    @BeforeEach
    void fixtures() {
        String suffix = UUID.randomUUID().toString().substring(0, 7);
        category = "rec" + suffix;
        buyer = user("r" + suffix);
        other = user("s" + suffix);
    }

    @Test
    void allPagesCoverEveryListedProductOnceIncludingSoldOut() {
        var expected = new HashSet<Integer>();
        for (int i = 0; i < 13; i++) expected.add(product("daily", "0-20", "0-20", "商品", i == 0 ? 0 : 10));
        int hidden = product("daily", "0-20", "0-20", "已下架", 10);
        jdbc.update("UPDATE MLgood SET state = 1 WHERE goodid = ?", hidden);
        var actual = new HashSet<Integer>();
        for (int page = 1; page <= 4; page++) {
            var result = recommendations.page(category, page, 4, null);
            assertEquals(13, result.total());
            result.items().forEach(g -> assertTrue(actual.add(g.getGoodid()), "Duplicate across pages"));
        }
        assertEquals(expected, actual);
        assertEquals(0, recommendations.page(category, 4, 4, null).items().get(0).getNumber());
        assertTrue(recommendations.page(category, 5, 4, null).items().isEmpty());
    }

    @Test
    void guestPopularityAndColdStartAreDeterministic() {
        int popular = product("dry", "0-20", "0-20", "畅销", 10);
        product("wet", "0-20", "0-20", "新品", 10);
        order(other, popular, 1);
        assertEquals(popular, first(null));
        assertEquals(popular, first(buyer));
        assertEquals(ids(null), ids(null));
    }

    @Test
    void viewsBoostRelatedProductsAndStayPrivateAcrossAccounts() {
        int viewed = product("dry", "0-20", "0-20", "浏览过", 10);
        int related = product("dry", "0-20", "0-20", "同类", 10);
        int newer = product("wet", "0-20", "0-20", "新品", 10);
        recommendations.recordView(buyer.getUserid(), viewed);
        recommendations.recordView(buyer.getUserid(), viewed);
        assertEquals(1, jdbc.queryForObject("SELECT visits FROM MLproduct_view WHERE userid = ? AND goodid = ?",
                Integer.class, buyer.getUserid(), viewed));
        assertEquals(List.of(viewed, related, newer), ids(buyer));
        assertEquals(newer, first(other));
        assertEquals(newer, first(null));
        jdbc.update("UPDATE MLproduct_view SET last_viewed = CURRENT_TIMESTAMP - INTERVAL 91 DAY WHERE userid = ?", buyer.getUserid());
        assertEquals(newer, first(buyer));
    }

    @Test
    void purchasesUseBuyerAccountAndIgnoreCancelledOrders() {
        int purchased = product("dry", "0-20", "0-20", "已购", 10);
        int cancelled = product("wet", "0-20", "0-20", "已取消", 10);
        int unrelated = product("toy", "0-20", "0-20", "新品", 10);
        order(buyer, purchased, 1);
        order(buyer, cancelled, -1);
        assertEquals(purchased, first(buyer));
        // The seller's account must not inherit the buyer's purchasing preference.
        assertEquals(purchased, first(other)); // Only the public sales signal applies.
        assertTrue(ids(buyer).indexOf(cancelled) > ids(buyer).indexOf(unrelated));
    }

    @Test
    void favouritesContributeToPersonalPreference() {
        int favourite = product("dry", "0-20", "0-20", "收藏", 10);
        int related = product("dry", "0-20", "0-20", "同类", 10);
        int newest = product("wet", "0-20", "0-20", "新品", 10);
        jdbc.update("INSERT INTO MLbuying(goodid,number,islike,buyer) VALUES (?,1,1,?)", favourite, buyer.getUserid());
        assertEquals(List.of(favourite, related, newest), ids(buyer));
    }

    @Test
    void catFitAndLifeStageAffectRankWithoutRemovingOtherGoods() {
        int kitten = product("dry", "0-1", "0-5", "幼猫奶糕", 10);
        int adult = product("dry", "1-15", "1-10", "成猫主粮", 10);
        int malformed = product("dry", "unknown", "unknown", "普通用品", 10);
        cat(buyer, 0, 2, 7);
        cat(other, 8, 5, 3);
        assertEquals(kitten, first(buyer));
        assertEquals(adult, first(other));
        assertEquals(3, ids(buyer).size());
        assertTrue(ids(buyer).contains(malformed));
        // Match the best fitting owned cat when a household has more than one cat.
        cat(buyer, 8, 5, 3);
        assertTrue(ids(buyer).indexOf(adult) < ids(buyer).indexOf(malformed));
    }

    @Test
    void catStateBreaksTiesForOtherwiseEquallySuitableProducts() {
        int weightControl = product("dry", "0-20", "0-20", "低脂体重管理", 10);
        int adult = product("dry", "0-20", "0-20", "成猫主粮", 10);
        cat(buyer, 3, 5, 1);
        cat(other, 3, 5, 3);
        assertEquals(weightControl, first(buyer));
        assertEquals(adult, first(other));
    }

    private int first(Usr user) { return ids(user).get(0); }
    private List<Integer> ids(Usr user) {
        return recommendations.page(category, 1, 48, user).items().stream().map(g -> g.getGoodid()).toList();
    }
    private Usr user(String name) {
        int id = insert("INSERT INTO MLuser(username,pwd,power,question,answer) VALUES (?,'test',0,'test','test')", name);
        Usr user = new Usr(); user.setUserid(id); user.setUsername(name); user.setPower(0); return user;
    }
    private int product(String subkind, String ages, String weights, String name, int stock) {
        return insert("""
                INSERT INTO MLgood(goodname,description,price,picture,state,number,kind,subkind,owner,calorie,catkind,catage,catweight)
                VALUES (?,'测试',10,'test.jpg',0,?,?,?, ?,4,'通用',?,?)
                """, name, stock, category, subkind, other.getUserid(), ages, weights);
    }
    private void order(Usr account, int good, int state) {
        jdbc.update("INSERT INTO MLorder(buyername,goodid,number,orderstate,owner) VALUES (?,?,2,?,?)",
                account.getUsername(), good, state, other.getUserid());
    }
    private void cat(Usr account, int age, double weight, int state) {
        jdbc.update("INSERT INTO MLcat(catname,description,catweight,catstate,catage,catkind,owner) VALUES ('猫','',?,?,?,'布偶猫',?)",
                weight, state, age, account.getUserid());
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
