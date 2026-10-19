package com.example.maoliang;

import com.example.maoliang.Entity.Order;
import com.example.maoliang.Entity.Usr;
import com.example.maoliang.Repository.OrderRepository;
import com.example.maoliang.Service.OrderService;
import com.example.maoliang.dto.CreateOrderData;
import com.example.maoliang.dto.CreateCartOrderData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Statement;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class CheckoutIntegrationTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired OrderService service;
    @Autowired MockMvc mvc;
    @SpyBean OrderRepository repository;
    private Usr buyer;
    private int sellerId;
    private int goodId;
    private final List<Integer> extraGoodIds = new ArrayList<>();

    @BeforeEach
    void createFixtures() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        buyer = new Usr();
        buyer.setUsername("b" + suffix);
        buyer.setUserid(insert("INSERT INTO MLuser(username,pwd,power,question,answer) VALUES (?, 'test', 0, 'test', 'test')", buyer.getUsername()));
        sellerId = insert("INSERT INTO MLuser(username,pwd,power,question,answer) VALUES (?, 'test', 1, 'test', 'test')", "s" + suffix);
        goodId = insert("INSERT INTO MLgood(goodname,description,price,picture,state,number,kind,subkind,owner,calorie,catkind,catage,catweight) " +
                "VALUES ('checkout-test','test',7.5,'test.jpg',0,5,'猫咪主粮','test',?,1,'test','1-10','1-7')", sellerId);
    }

    @AfterEach
    void removeFixtures() {
        // Remove only the synthetic records owned by this test, leaving demo data intact.
        jdbc.update("DELETE FROM MLbuying WHERE buyer = ?", buyer.getUserid());
        jdbc.update("DELETE FROM MLorder WHERE goodid = ?", goodId);
        jdbc.update("DELETE FROM MLgood WHERE goodid = ?", goodId);
        for (int id : extraGoodIds) {
            jdbc.update("DELETE FROM rag_catalog_outbox WHERE good_id = ?", id);
            jdbc.update("DELETE FROM MLorder WHERE goodid = ?", id);
            jdbc.update("DELETE FROM MLgood WHERE goodid = ?", id);
        }
        jdbc.update("DELETE FROM rag_catalog_outbox WHERE good_id = ?", goodId);
        jdbc.update("DELETE FROM MLuser WHERE userid IN (?, ?)", buyer.getUserid(), sellerId);
    }

    @Test
    void checkoutPersistsRecipientAndUsesAccountForHistory() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("admin", buyer);
        mvc.perform(post("/order/createorder-control").session(session).contentType("application/json")
                .content("{\"goodid\":" + goodId + ",\"number\":2,\"buyerName\":\"测试收货人\",\"telephone\":\"12345678901\",\"address\":\"测试地址\",\"owner\":-1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.msg").value("下单成功"))
                .andExpect(jsonPath("$.data.orderid").isNumber())
                .andExpect(jsonPath("$.data.buyername").value(buyer.getUsername()))
                .andExpect(jsonPath("$.data.recipientname").value("测试收货人"))
                .andExpect(jsonPath("$.data.owner").value(sellerId))
                .andExpect(jsonPath("$.data.orderstate").value(1));
        assertEquals(3, stock());
        assertEquals(1, service.showbuyerorderinfo(buyer.getUsername()).size());
    }

    @Test
    void rejectsAnonymousAndInvalidQuantitiesWithoutChangingStock() throws Exception {
        mvc.perform(post("/order/createorder-control").contentType("application/json")
                .content("{\"goodid\":" + goodId + ",\"number\":1}"))
                .andExpect(jsonPath("$.page").value("/error"));
        for (int quantity : new int[]{0, -1, 6}) {
            assertThrows(IllegalArgumentException.class, () -> service.createOrder(request(quantity, false), buyer));
        }
        assertThrows(IllegalArgumentException.class, () -> service.createOrder(
                new CreateOrderData(goodId, 1, "测试收货人", "invalid", "测试地址", false), buyer));
        assertEquals(5, stock());
        assertEquals(0, countOrders());
    }

    @Test
    void rejectsFractionalQuantity() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("admin", buyer);
        mvc.perform(post("/order/createorder-control").session(session).contentType("application/json")
                .content("{\"goodid\":" + goodId + ",\"number\":1.5,\"buyerName\":\"测试收货人\",\"telephone\":\"12345678901\",\"address\":\"测试地址\"}"))
                .andExpect(status().isBadRequest());
        assertEquals(5, stock());
        assertEquals(0, countOrders());
    }

    @Test
    void concurrentCheckoutsDoNotOversell() throws Exception {
        jdbc.update("UPDATE MLgood SET number = 1 WHERE goodid = ?", goodId);
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        java.util.concurrent.Callable<Boolean> checkout = () -> {
            start.await();
            try { service.createOrder(request(1, false), buyer); return true; }
            catch (IllegalArgumentException exception) { return false; }
        };
        try {
            var first = executor.submit(checkout);
            var second = executor.submit(checkout);
            start.countDown();
            assertNotEquals(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS));
            assertEquals(0, stock());
            assertEquals(1, countOrders());
        } finally { executor.shutdownNow(); }
    }

    @Test
    void rejectsUnavailableGoods() {
        jdbc.update("UPDATE MLgood SET state = 1 WHERE goodid = ?", goodId);
        assertThrows(IllegalArgumentException.class, () -> service.createOrder(request(1, false), buyer));
        assertEquals(5, stock());
        assertEquals(0, countOrders());
    }

    @Test
    void rollsBackStockWhenOrderInsertFails() {
        doThrow(new DataIntegrityViolationException("test insert failure")).when(repository).create(any(Order.class));
        assertThrows(DataIntegrityViolationException.class, () -> service.createOrder(request(2, false), buyer));
        assertEquals(5, stock());
        assertEquals(0, countOrders());
    }

    @Test
    void cartCheckoutConsumesQuantityAndPreservesFavourite() {
        jdbc.update("INSERT INTO MLbuying(goodid,number,islike,buyer) VALUES (?,2,1,?)", goodId, buyer.getUserid());
        service.createOrder(request(2, true), buyer);
        assertEquals(0, jdbc.queryForObject("SELECT number FROM MLbuying WHERE goodid = ? AND buyer = ?", Integer.class, goodId, buyer.getUserid()));
        assertEquals(1, jdbc.queryForObject("SELECT islike FROM MLbuying WHERE goodid = ? AND buyer = ?", Integer.class, goodId, buyer.getUserid()));
        assertEquals(3, stock());
    }

    @Test
    void cancellationRestoresStockOnlyOnceAndChecksOwnership() {
        Order order = service.createOrder(request(2, false), buyer);
        Usr stranger = new Usr();
        stranger.setUserid(-1);
        stranger.setUsername("stranger");
        assertFalse(service.deleteOrder(order.getOrderid(), -1, stranger));
        assertEquals(3, stock());
        assertTrue(service.deleteOrder(order.getOrderid(), -1, buyer));
        assertEquals(5, stock());
        assertFalse(service.deleteOrder(order.getOrderid(), -1, buyer));
        assertEquals(5, stock());
    }

    private CreateOrderData request(int quantity, boolean fromCart) {
        return new CreateOrderData(goodId, quantity, "测试收货人", "12345678901", "测试地址", fromCart);
    }

    @Test
    void batchCheckoutConsumesOnlySelectedGoodsAndKeepsFavourites() throws Exception {
        int second = extraGood();
        int unselected = extraGood();
        addCart(goodId, 2);
        addCart(second, 3);
        addCart(unselected, 1);
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("admin", buyer);
        mvc.perform(post("/order/checkout-cart").session(session).contentType("application/json")
                .content("{\"items\":[{\"goodid\":" + second + ",\"number\":3},{\"goodid\":" + goodId
                        + ",\"number\":2}],\"buyerName\":\"测试收货人\",\"telephone\":\"12345678901\",\"address\":\"测试地址\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].recipientname").value("测试收货人"))
                .andExpect(jsonPath("$.data[1].recipientname").value("测试收货人"));
        assertEquals(3, stock());
        assertEquals(2, stock(second));
        assertEquals(5, stock(unselected));
        assertEquals(0, cartQuantity(goodId));
        assertEquals(0, cartQuantity(second));
        assertEquals(1, cartQuantity(unselected));
        assertEquals(1, jdbc.queryForObject("SELECT islike FROM MLbuying WHERE goodid = ? AND buyer = ?",
                Integer.class, goodId, buyer.getUserid()));
        assertEquals(2, service.showbuyerorderinfo(buyer.getUsername()).size());
    }

    @Test
    void batchCheckoutRollsBackEarlierItemsWhenLaterStockIsInsufficient() {
        int second = extraGood();
        addCart(goodId, 2);
        addCart(second, 6);
        assertThrows(IllegalArgumentException.class, () -> service.createCartOrders(batch(second, 6), buyer));
        assertBatchUntouched(second, 6);
    }

    @Test
    void batchCheckoutRollsBackEarlierItemsWhenLaterOrderInsertFails() {
        int second = extraGood();
        addCart(goodId, 2);
        addCart(second, 1);
        org.mockito.Mockito.doAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            if (order.getGoodid() == second) throw new DataIntegrityViolationException("test second insert failure");
            return invocation.callRealMethod();
        }).when(repository).create(any(Order.class));
        assertThrows(DataIntegrityViolationException.class, () -> service.createCartOrders(batch(second, 1), buyer));
        assertBatchUntouched(second, 1);
    }

    @Test
    void batchCheckoutRejectsInvalidSelectionsWithoutChanges() throws Exception {
        addCart(goodId, 2);
        assertThrows(IllegalArgumentException.class, () -> service.createCartOrders(null, buyer));
        assertThrows(IllegalArgumentException.class, () -> service.createCartOrders(
                new CreateCartOrderData(List.of(), "测试收货人", "12345678901", "测试地址"), buyer));
        assertThrows(IllegalArgumentException.class, () -> service.createCartOrders(batch(goodId, 1), buyer));
        assertThrows(IllegalArgumentException.class, () -> service.createCartOrders(batch(goodId, 0), buyer));
        mvc.perform(post("/order/checkout-cart").contentType("application/json").content("{\"items\":[]}"))
                .andExpect(jsonPath("$.page").value("/error"));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("admin", buyer);
        mvc.perform(post("/order/checkout-cart").session(session).contentType("application/json")
                .content("{\"items\":[{\"goodid\":" + goodId + ",\"number\":1.5}]}"))
                .andExpect(status().isBadRequest());
        assertEquals(5, stock());
        assertEquals(2, cartQuantity(goodId));
        assertEquals(0, countOrders());
    }

    @Test
    void batchCheckoutRejectsGoodsOutsideBuyersCartAndRollsBack() {
        int second = extraGood();
        addCart(goodId, 2);
        jdbc.update("INSERT INTO MLbuying(goodid,number,islike,buyer) VALUES (?,1,0,?)", second, sellerId);
        try {
            assertThrows(IllegalArgumentException.class, () -> service.createCartOrders(batch(second, 1), buyer));
            assertEquals(5, stock());
            assertEquals(5, stock(second));
            assertEquals(2, cartQuantity(goodId));
            assertEquals(0, countOrders());
        } finally { jdbc.update("DELETE FROM MLbuying WHERE goodid = ?", second); }
    }

    @Test
    void concurrentBatchCheckoutOfSameCartSucceedsOnlyOnce() throws Exception {
        int second = extraGood();
        addCart(goodId, 2);
        addCart(second, 1);
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        java.util.concurrent.Callable<Boolean> checkout = () -> {
            start.await();
            try { service.createCartOrders(batch(second, 1), buyer); return true; }
            catch (IllegalArgumentException exception) { return false; }
        };
        try {
            var first = executor.submit(checkout);
            var other = executor.submit(checkout);
            start.countDown();
            assertNotEquals(first.get(10, TimeUnit.SECONDS), other.get(10, TimeUnit.SECONDS));
            assertEquals(3, stock());
            assertEquals(4, stock(second));
            assertEquals(2, service.showbuyerorderinfo(buyer.getUsername()).size());
        } finally { executor.shutdownNow(); }
    }

    private int extraGood() {
        int id = insert("INSERT INTO MLgood(goodname,description,price,picture,state,number,kind,subkind,owner,calorie,catkind,catage,catweight) " +
                "VALUES ('batch-checkout-test','test',8.5,'test.jpg',0,5,'猫咪主粮','test',?,1,'test','1-10','1-7')", sellerId);
        extraGoodIds.add(id);
        return id;
    }
    private void addCart(int id, int quantity) {
        jdbc.update("INSERT INTO MLbuying(goodid,number,islike,buyer) VALUES (?,?,1,?)", id, quantity, buyer.getUserid());
    }
    private CreateCartOrderData batch(int second, int quantity) {
        return new CreateCartOrderData(List.of(new CreateCartOrderData.Item(goodId, 2),
                new CreateCartOrderData.Item(second, quantity)), "测试收货人", "12345678901", "测试地址");
    }
    private void assertBatchUntouched(int second, int quantity) {
        assertEquals(5, stock());
        assertEquals(5, stock(second));
        assertEquals(2, cartQuantity(goodId));
        assertEquals(quantity, cartQuantity(second));
        assertEquals(0, service.showbuyerorderinfo(buyer.getUsername()).size());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM rag_catalog_outbox WHERE good_id IN (?,?)", Integer.class, goodId, second));
    }
    private int stock(int id) { return jdbc.queryForObject("SELECT number FROM MLgood WHERE goodid = ?", Integer.class, id); }
    private int cartQuantity(int id) { return jdbc.queryForObject("SELECT number FROM MLbuying WHERE goodid = ? AND buyer = ?", Integer.class, id, buyer.getUserid()); }
    private int stock() { return jdbc.queryForObject("SELECT number FROM MLgood WHERE goodid = ?", Integer.class, goodId); }
    private int countOrders() { return jdbc.queryForObject("SELECT COUNT(*) FROM MLorder WHERE goodid = ?", Integer.class, goodId); }
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
