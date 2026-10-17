package com.example.maoliang;

import com.example.maoliang.Entity.Good;
import com.example.maoliang.Entity.Order;
import com.example.maoliang.Entity.Usr;
import com.example.maoliang.Repository.GoodListRepository;
import com.example.maoliang.Service.GoodService;
import com.example.maoliang.Service.CatService;
import com.example.maoliang.Service.HotGoodsCache;
import com.example.maoliang.Service.OrderService;
import com.example.maoliang.dto.CreateOrderData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Statement;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@EnabledIfEnvironmentVariable(named = "KITTY_REDIS_INTEGRATION", matches = "true")
class HotGoodsIntegrationTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired StringRedisTemplate redis;
    @Autowired GoodService goods;
    @Autowired CatService cats;
    @Autowired OrderService orders;
    @Autowired HotGoodsCache cache;
    @Autowired TransactionTemplate transaction;
    @Autowired MockMvc mvc;
    @SpyBean GoodListRepository repository;
    int seller;
    Usr buyer;
    int popular;
    int other;
    int soldOut;
    int hidden;

    @BeforeEach
    void setup() {
        String suffix = UUID.randomUUID().toString().substring(0, 7);
        seller = insert("INSERT INTO MLuser(username,pwd,power,question,answer) VALUES (?,'test',1,'test','test')", "s" + suffix);
        buyer = new Usr();
        buyer.setUsername("b" + suffix);
        buyer.setUserid(insert("INSERT INTO MLuser(username,pwd,power,question,answer) VALUES (?,'test',0,'test','test')", buyer.getUsername()));
        popular = product("popular", 0, 10);
        other = product("other", 0, 10);
        soldOut = product("sold-out", 0, 0);
        hidden = product("hidden", 1, 10);
        order(popular, 1000, 4);
        order(other, 9999, -1);
        order(soldOut, 9999, 4);
        order(hidden, 9999, 4);
        cache.invalidateAfterCommit();
        clearInvocations(repository);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM MLcat WHERE owner = ?", buyer.getUserid());
        jdbc.update("DELETE FROM MLbuying WHERE buyer = ?", buyer.getUserid());
        jdbc.update("DELETE FROM MLorder WHERE owner = ?", seller);
        jdbc.update("DELETE FROM MLgood WHERE owner = ?", seller);
        jdbc.update("DELETE FROM MLuser WHERE userid IN (?,?)", seller, buyer.getUserid());
        cache.invalidateAfterCommit();
    }

    @Test
    void foodRecommendationLoadsCategoryAndAvailabilityFromDatabase() {
        jdbc.update("INSERT INTO MLcat(catname,description,catweight,catstate,catage,catkind,owner) " +
                "VALUES ('cache-test','test',4,3,2,'test',?)", buyer.getUserid());
        int daily = product("daily", 0, 5);
        jdbc.update("UPDATE MLgood SET kind = '猫咪日用', calorie = 0 WHERE goodid = ?", daily);
        var recommendations = cats.showRecommend(buyer.getUserid());
        assertEquals(2, recommendations.size());
        assertTrue(recommendations.stream().allMatch(g -> g.getGoodid() == popular || g.getGoodid() == other));
        assertTrue(recommendations.stream().allMatch(g -> Double.isFinite(g.getWeight()) && g.getWeight() > 0));
    }

    @Test
    void publicEndpointCachesRankedAvailableProducts() throws Exception {
        mvc.perform(get("/good/hot-goods")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].goodid").value(popular));
        List<Good> cached = goods.showHotGoods();
        assertTrue(cached.size() <= 8);
        assertTrue(cached.stream().noneMatch(g -> g.getGoodid() == soldOut || g.getGoodid() == hidden));
        verify(repository, times(1)).showHotGoods();
        assertTrue(redis.getExpire(HotGoodsCache.KEY) > 0);
        assertTrue(redis.getExpire(HotGoodsCache.KEY) <= 300);
    }

    @Test
    void favouritesBreakSalesTiesAndInvalidateCache() {
        order(other, 1000, 4);
        goods.showHotGoods();
        goods.addToLike(popular, buyer.getUserid());
        assertFalse(redis.hasKey(HotGoodsCache.KEY));
        assertEquals(popular, goods.showHotGoods().get(0).getGoodid());
        goods.cancelLike(popular, buyer.getUserid());
        assertEquals(other, goods.showHotGoods().get(0).getGoodid());
    }

    @Test
    void priceChangeAndRemovalInvalidateCache() {
        goods.showHotGoods();
        Good product = goods.showGood(popular);
        product.setPrice(27.5);
        assertTrue(goods.updateGood(product));
        assertFalse(redis.hasKey(HotGoodsCache.KEY));
        assertEquals(27.5, goods.showHotGoods().get(0).getPrice());
        orders.deletegood(popular);
        goods.remove(popular);
        assertTrue(goods.showHotGoods().stream().noneMatch(g -> g.getGoodid() == popular));
    }

    @Test
    void checkoutAndCancellationRefreshInventoryAfterCommit() {
        goods.showHotGoods();
        Order order = orders.createOrder(new CreateOrderData(popular, 2, "测试", "12345678901", "测试地址", false), buyer);
        assertFalse(redis.hasKey(HotGoodsCache.KEY));
        assertEquals(8, goods.showHotGoods().get(0).getNumber());
        assertTrue(orders.deleteOrder(order.getOrderid(), -1, buyer));
        assertEquals(10, goods.showHotGoods().get(0).getNumber());
    }

    @Test
    void rolledBackPriceChangeKeepsOriginalCachedPrice() {
        double price = goods.showHotGoods().get(0).getPrice();
        String original = redis.opsForValue().get(HotGoodsCache.KEY);
        transaction.executeWithoutResult(status -> {
            Good product = goods.showGood(popular);
            product.setPrice(999);
            goods.updateGood(product);
            assertEquals(original, redis.opsForValue().get(HotGoodsCache.KEY));
            status.setRollbackOnly();
        });
        assertEquals(original, redis.opsForValue().get(HotGoodsCache.KEY));
        assertEquals(price, goods.showGood(popular).getPrice());
    }

    @Test
    void expiredAndCorruptedEntriesReloadFromMysql() throws Exception {
        goods.showHotGoods();
        redis.expire(HotGoodsCache.KEY, java.time.Duration.ofMillis(1));
        Thread.sleep(20);
        goods.showHotGoods();
        redis.opsForValue().set(HotGoodsCache.KEY, "broken json");
        assertEquals(popular, goods.showHotGoods().get(0).getGoodid());
        verify(repository, times(3)).showHotGoods();
    }

    @Test
    void inFlightReadCannotRepopulateOldDataAfterMutation() throws Exception {
        var loaded = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        try {
            var pending = executor.submit(() -> cache.getOrLoad(() -> {
                List<Good> snapshot = repository.showHotGoods();
                loaded.countDown();
                try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
                catch (InterruptedException exception) { throw new RuntimeException(exception); }
                return snapshot;
            }));
            assertTrue(loaded.await(5, TimeUnit.SECONDS));
            Good product = goods.showGood(popular);
            product.setPrice(88);
            goods.updateGood(product);
            release.countDown();
            assertEquals(19.9, pending.get(5, TimeUnit.SECONDS).get(0).getPrice());
            assertFalse(redis.hasKey(HotGoodsCache.KEY));
            assertEquals(88, goods.showHotGoods().get(0).getPrice());
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    private int product(String name, int state, int stock) {
        return insert("INSERT INTO MLgood(goodname,description,price,picture,state,number,kind,subkind,owner,calorie,catkind,catage,catweight) " +
                "VALUES (?,'hot-cache-test',19.9,'test.jpg',?,?,'猫咪主粮','test',?,1,'test','1-10','1-12')", name, state, stock, seller);
    }
    private void order(int product, int quantity, int state) {
        jdbc.update("INSERT INTO MLorder(goodid,number,orderstate,owner) VALUES (?,?,?,?)", product, quantity, state, seller);
    }
    private int insert(String sql, Object... args) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            return statement;
        }, key);
        return key.getKey().intValue();
    }
}
