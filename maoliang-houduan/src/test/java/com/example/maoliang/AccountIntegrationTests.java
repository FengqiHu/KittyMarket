package com.example.maoliang;

import com.example.maoliang.Entity.Usr;
import com.example.maoliang.Service.AccountService;
import com.example.maoliang.Service.OrderService;
import com.example.maoliang.dto.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AccountIntegrationTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired AccountService accounts;
    @Autowired OrderService orders;
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    private int buyerId, otherId, goodId;
    private Usr buyer;
    private MockHttpSession session;

    @BeforeEach void fixtures() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        buyerId = insert("INSERT INTO MLuser(username,pwd,power,question,answer) VALUES (?, 'legacy',0,'test','private-answer')", "a" + suffix);
        otherId = insert("INSERT INTO MLuser(username,pwd,power,question,answer) VALUES (?, 'legacy',0,'test','private-answer')", "b" + suffix);
        goodId = insert("INSERT INTO MLgood(goodname,description,price,picture,state,number,kind,subkind,owner,calorie,catkind,catage,catweight) VALUES ('account-test','test',7.5,'test.jpg',0,5,'test','test',?,1,'test','1-10','1-7')", otherId);
        buyer = accounts.current(buyerId);
        session = new MockHttpSession(); session.setAttribute("admin", buyer);
    }

    @AfterEach void cleanup() {
        jdbc.update("DELETE FROM rag_catalog_outbox WHERE good_id = ?", goodId);
        jdbc.update("DELETE FROM MLorder WHERE goodid = ?", goodId);
        jdbc.update("DELETE FROM MLgood WHERE goodid = ?", goodId);
        jdbc.update("DELETE FROM MLinfo WHERE userid IN (?,?)", buyerId, otherId);
        jdbc.update("DELETE FROM MLuser WHERE userid IN (?,?)", buyerId, otherId);
    }

    @Test void addressesRequireAuthenticationAndUseSessionOwner() throws Exception {
        mvc.perform(get("/account/addresses")).andExpect(status().isUnauthorized());
        mvc.perform(post("/account/addresses").session(session).contentType("application/json")
                .content(json.writeValueAsString(Map.of("recipient","收货人","telephone","12345678901","address","测试地址","user_id",otherId))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.id").isNumber()).andExpect(jsonPath("$.data.isDefault").value(true));
        assertEquals(1, accounts.addresses(buyerId).size());
        assertTrue(accounts.addresses(otherId).isEmpty());
    }

    @Test void preservesLegacyAddressOnceAndDoesNotResurrectDeletedAddresses() {
        jdbc.update("INSERT INTO MLinfo(userid,phone,address) VALUES (?,'12345678901','原有地址')", buyerId);
        var addresses = accounts.addresses(buyerId);
        assertEquals(1, addresses.size());
        assertEquals("原有地址", addresses.get(0).address());
        assertEquals(1, accounts.addresses(buyerId).size());
        accounts.deleteAddress(buyerId, addresses.get(0).id());
        assertTrue(accounts.addresses(buyerId).isEmpty());
        assertTrue(accounts.addresses(buyerId).isEmpty());
        assertNull(accounts.current(buyerId).getAddress());
    }

    @Test void createsEditsDefaultsAndPromotesRemainingAddressAfterDeletion() {
        var first = accounts.saveAddress(buyerId, null, address("第一地址", false));
        var second = accounts.saveAddress(buyerId, null, address("第二地址", false));
        assertTrue(first.isDefault());
        assertFalse(second.isDefault());
        accounts.saveAddress(buyerId, second.id(), address("编辑后的地址", false));
        accounts.defaultAddress(buyerId, second.id());
        assertEquals(second.id(), accounts.addresses(buyerId).get(0).id());
        assertEquals("编辑后的地址", accounts.current(buyerId).getAddress());
        assertEquals(1, accounts.addresses(buyerId).stream().filter(ShippingAddress::isDefault).count());
        accounts.deleteAddress(buyerId, second.id());
        assertTrue(accounts.addresses(buyerId).get(0).isDefault());
    }

    @Test void cannotReadOrMutateAnotherUsersAddresses() throws Exception {
        var own = accounts.saveAddress(buyerId, null, address("自己的地址", true));
        var other = accounts.saveAddress(otherId, null, address("别人的地址", true));
        mvc.perform(get("/account/addresses").session(session)).andExpect(jsonPath("$.data.length()").value(1)).andExpect(jsonPath("$.data[0].id").value(own.id()));
        mvc.perform(put("/account/addresses/" + other.id()).session(session).contentType("application/json").content(json.writeValueAsString(address("覆盖地址", true)))).andExpect(status().isNotFound());
        mvc.perform(delete("/account/addresses/" + other.id()).session(session)).andExpect(status().isNotFound());
        mvc.perform(put("/account/addresses/" + other.id() + "/default").session(session)).andExpect(status().isNotFound());
        assertEquals("别人的地址", accounts.addresses(otherId).get(0).address());
        assertTrue(accounts.addresses(buyerId).get(0).isDefault());
    }

    @Test void invalidAddressCannotReplaceValidAddress() {
        var saved = accounts.saveAddress(buyerId, null, address("原地址", true));
        for (var invalid : List.of(new AddressData("", "12345678901", "地址", true), new AddressData("收货人", "invalid", "地址", true), new AddressData("收货人", "12345678901", "", true))) {
            assertThrows(IllegalArgumentException.class, () -> accounts.saveAddress(buyerId, saved.id(), invalid));
        }
        assertEquals("原地址", accounts.addresses(buyerId).get(0).address());
    }

    @Test void concurrentDefaultChangesStillLeaveExactlyOneDefault() throws Exception {
        var first = accounts.saveAddress(buyerId, null, address("第一地址", false));
        var second = accounts.saveAddress(buyerId, null, address("第二地址", false));
        var executor = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            var a = executor.submit(() -> { start.await(); accounts.defaultAddress(buyerId, first.id()); return true; });
            var b = executor.submit(() -> { start.await(); accounts.defaultAddress(buyerId, second.id()); return true; });
            start.countDown(); a.get(10, TimeUnit.SECONDS); b.get(10, TimeUnit.SECONDS);
            assertEquals(1, accounts.addresses(buyerId).stream().filter(ShippingAddress::isDefault).count());
        } finally { executor.shutdownNow(); }
    }

    @Test void renameKeepsHistoryAndStaleSessionsCreateOrdersUnderCurrentName() throws Exception {
        var first = orders.createOrder(orderRequest("历史地址"), buyer);
        String oldName = buyer.getUsername();
        String newName = "r" + UUID.randomUUID().toString().substring(0, 8);
        mvc.perform(put("/account/name").session(session).contentType("application/json").content(json.writeValueAsString(new AccountNameData(newName))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.username").value(newName));
        orders.createOrder(orderRequest("新地址"), buyer);
        assertEquals(2, orders.showbuyerorderinfo(newName).size());
        assertTrue(orders.showbuyerorderinfo(oldName).isEmpty());
        assertEquals("测试收货人", jdbc.queryForObject("SELECT recipientname FROM MLorder WHERE orderid = ?", String.class, first.getOrderid()));
        MockHttpSession oldSession = new MockHttpSession(); oldSession.setAttribute("admin", buyer);
        mvc.perform(get("/now-usr").session(oldSession)).andExpect(jsonPath("$.username").value(newName));
        assertThrows(IllegalArgumentException.class, () -> accounts.rename(buyerId, new AccountNameData(accounts.current(otherId).getUsername())));
        assertEquals(newName, accounts.current(buyerId).getUsername());
    }

    @Test void passwordChangeValidatesCurrentPasswordStoresHashAndNewPasswordCanLogIn() throws Exception {
        mvc.perform(put("/account/password").session(session).contentType("application/json").content(json.writeValueAsString(new AccountPasswordData("wrong", "new-password-123", "new-password-123")))).andExpect(status().isBadRequest());
        assertEquals("legacy", accounts.current(buyerId).getPwd());
        mvc.perform(put("/account/password").session(session).contentType("application/json").content(json.writeValueAsString(new AccountPasswordData("legacy", "new-password-123", "new-password-123")))).andExpect(status().isOk());
        String stored = accounts.current(buyerId).getPwd();
        assertTrue(stored.startsWith("{pbkdf2}"));
        assertNotEquals("new-password-123", stored);
        mvc.perform(post("/usr/login-control").contentType("application/json").content(json.writeValueAsString(Map.of("username", buyer.getUsername(), "password", "legacy")))).andExpect(jsonPath("$.page").value("/error"));
        mvc.perform(post("/usr/login-control").contentType("application/json").content(json.writeValueAsString(Map.of("username", buyer.getUsername(), "password", "new-password-123")))).andExpect(jsonPath("$.page").value("/buyer"));
        mvc.perform(get("/account").session(session)).andExpect(jsonPath("$.data.pwd").doesNotExist()).andExpect(jsonPath("$.data.answer").doesNotExist());
        mvc.perform(get("/now-usr").session(session)).andExpect(jsonPath("$.pwd").doesNotExist()).andExpect(jsonPath("$.answer").doesNotExist());
    }

    @Test void editingAndDeletingAddressDoesNotChangeSubmittedOrderSnapshot() {
        var saved = accounts.saveAddress(buyerId, null, address("下单时的地址", true));
        var order = orders.createOrder(orderRequest(saved.address()), buyer);
        accounts.saveAddress(buyerId, saved.id(), address("新地址", true));
        accounts.deleteAddress(buyerId, saved.id());
        assertEquals("下单时的地址", jdbc.queryForObject("SELECT address FROM MLorder WHERE orderid = ?", String.class, order.getOrderid()));
    }

    private AddressData address(String address, boolean isDefault) { return new AddressData("测试收货人", "12345678901", address, isDefault); }
    private CreateOrderData orderRequest(String address) { return new CreateOrderData(goodId, 1, "测试收货人", "12345678901", address, false); }
    private int insert(String sql, Object... values) {
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> { var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS); for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]); return statement; }, key);
        return key.getKey().intValue();
    }
}
