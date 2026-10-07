package com.example.maoliang;

import com.example.maoliang.Entity.Order;
import com.example.maoliang.Entity.Usr;
import com.example.maoliang.Repository.OrderRepository;
import com.example.maoliang.Service.OrderService;
import com.example.maoliang.dto.CreateOrderData;
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
