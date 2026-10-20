package com.example.maoliang;

import com.example.maoliang.Config.RateLimitFilter;
import com.example.maoliang.Config.RateLimitProperties;
import com.example.maoliang.Config.RateLimitProperties.Bucket;
import com.example.maoliang.Entity.Usr;
import com.example.maoliang.Service.RequestRateLimiter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {"app.rag.mq.enabled=false", "app.rag.enabled=false"})
class RateLimitIntegrationTests {
    @Autowired StringRedisTemplate redis;
    @Autowired ObjectMapper mapper;
    private final List<String> identities = new ArrayList<>();
    private RequestRateLimiter limiter;
    private MockMvc mvc;
    private String address;
    private int userId;
    private Probe probe;

    @BeforeEach
    void setup() {
        userId = UUID.randomUUID().hashCode() & Integer.MAX_VALUE;
        address = "10.222." + ((userId >> 8) & 255) + "." + (userId & 255);
        identities.add("user:" + userId);
        identities.add("ip:" + address);
        limiter = new RequestRateLimiter(redis);
        var config = new RateLimitProperties(true, "", new Bucket(3, 1), new Bucket(1, 0.1),
                new Bucket(1, 0.1), new Bucket(1, 0.1));
        probe = new Probe();
        mvc = MockMvcBuilders.standaloneSetup(probe).addFilters(new RateLimitFilter(config, limiter, mapper)).build();
    }

    @AfterEach
    void cleanup() {
        for (String identity : identities) for (String group : List.of("general", "write", "ai", "auth", "atomic", "recovery")) {
            redis.delete("kittymarket:rate-limit:v1:{" + identity + "}:" + group);
        }
    }

    @Test
    void changingSessionAndUseridCannotResetTheUserBudgetAndRejectedRequestsNeverRun() throws Exception {
        for (int i = 0; i < 3; i++) mvc.perform(request("/probe").session(session(userId)).param("userid", "" + i)).andExpect(status().isOk());
        mvc.perform(request("/probe").session(session(userId)).param("userid", "999"))
                .andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After", "1"))
                .andExpect(jsonPath("$.page").value("/error"))
                .andExpect(jsonPath("$.data.code").value("RATE_LIMITED"));
        assertEquals(3, probe.calls.get());
        identities.add("user:" + (userId + 1));
        mvc.perform(request("/probe").session(session(userId + 1))).andExpect(status().isOk());
    }

    @Test
    void anonymousSessionsAndForgedForwardingHeadersShareTheSourceIpBudget() throws Exception {
        for (int i = 0; i < 3; i++) mvc.perform(request("/probe").session(new MockHttpSession())
                .header("X-Real-IP", "192.0.2." + i).header("X-Forwarded-For", "198.51.100." + i)).andExpect(status().isOk());
        mvc.perform(request("/probe").header("X-Real-IP", "203.0.113.50"))
                .andExpect(status().isTooManyRequests());
    }

    @Test
    void writesAreLimitedEvenThroughLegacyGetAndReadPostsUseOnlyTheGeneralBudget() throws Exception {
        var session = session(userId);
        mvc.perform(request("/cart/addLike").session(session)).andExpect(status().isOk());
        mvc.perform(request("/cart/addLike").session(session)).andExpect(status().isTooManyRequests());
        mvc.perform(post("/cart/showLike").servletPath("/cart/showLike").session(session)).andExpect(status().isOk());
        assertEquals(2, probe.calls.get());
    }

    @Test
    void aiAndAuthenticationHaveTheirOwnStricterBudget() throws Exception {
        mvc.perform(request("/good/rag-search").session(session(userId))).andExpect(status().isOk());
        mvc.perform(request("/good/rag-search").session(session(userId))).andExpect(status().isTooManyRequests());
        mvc.perform(request("/usr/login-control")).andExpect(status().isOk());
        mvc.perform(request("/usr/login-control")).andExpect(status().isTooManyRequests());
    }

    @Test
    void independentInstancesShareAnAtomicRedisBudgetAndKeysExpire() throws Exception {
        String identity = "user:" + userId;
        var otherInstance = new RequestRateLimiter(redis);
        var policy = new Bucket(4, 0.001);
        var pool = Executors.newFixedThreadPool(12);
        AtomicInteger admitted = new AtomicInteger();
        try {
            var tasks = java.util.stream.IntStream.range(0, 60).mapToObj(i -> pool.submit(() -> {
                var instance = i % 2 == 0 ? limiter : otherInstance;
                if (instance.acquire(identity, "atomic", policy) == 0) admitted.incrementAndGet();
            })).toList();
            for (var task : tasks) task.get();
            assertEquals(4, admitted.get());
            assertTrue(redis.getExpire("kittymarket:rate-limit:v1:{" + identity + "}:atomic") > 0);
            assertTrue(new RequestRateLimiter(redis).acquire(identity, "atomic", policy) > 0);
        } finally { pool.shutdownNow(); }
    }

    @Test
    void staticResourcesAndPreflightDoNotSpendTheApiBudget() throws Exception {
        for (int i = 0; i < 5; i++) mvc.perform(request("/catalog/images/test.jpg")).andExpect(status().isOk());
        mvc.perform(options("/probe").servletPath("/probe")).andExpect(status().isOk());
        assertFalse(Boolean.TRUE.equals(redis.hasKey("kittymarket:rate-limit:v1:{ip:" + address + "}:general")));
        mvc.perform(request("/probe")).andExpect(status().isOk());
    }

    @Test
    void theSharedBudgetReplenishesAfterTimePasses() {
        String identity = "user:" + userId;
        var policy = new Bucket(1, 1);
        assertEquals(0, limiter.acquire(identity, "recovery", policy));
        assertTrue(new RequestRateLimiter(redis).acquire(identity, "recovery", policy) > 0);
        redis.opsForHash().put("kittymarket:rate-limit:v1:{" + identity + "}:recovery", "updated",
                Long.toString(System.currentTimeMillis() - 2000));
        assertEquals(0, new RequestRateLimiter(redis).acquire(identity, "recovery", policy));
    }

    @Test
    void onlyTheConfiguredProxyCanProvideTheActualClientAddress() throws Exception {
        String forwarded = "192.0.2." + (userId & 255);
        identities.add("ip:" + forwarded);
        var config = new RateLimitProperties(true, "127.0.0.1", new Bucket(1, 0.1),
                new Bucket(1, 0.1), new Bucket(1, 0.1), new Bucket(1, 0.1));
        var proxied = MockMvcBuilders.standaloneSetup(probe)
                .addFilters(new RateLimitFilter(config, new RequestRateLimiter(redis), mapper)).build();
        proxied.perform(get("/probe").servletPath("/probe").header("X-Real-IP", forwarded)).andExpect(status().isOk());
        proxied.perform(get("/probe").servletPath("/probe").header("X-Real-IP", forwarded)).andExpect(status().isTooManyRequests());
        // A direct caller outside the trusted proxy still uses its connection address.
        proxied.perform(request("/probe").header("X-Real-IP", forwarded)).andExpect(status().isOk());
    }

    private MockHttpServletRequestBuilder request(String path) {
        return get(path).servletPath(path).with(request -> { request.setRemoteAddr(address); return request; });
    }
    private MockHttpSession session(int id) {
        Usr user = new Usr(); user.setUserid(id);
        var session = new MockHttpSession(); session.setAttribute("admin", user); return session;
    }
    @RestController
    @TestComponent
    static class Probe {
        final AtomicInteger calls = new AtomicInteger();
        @RequestMapping({"/probe", "/cart/addLike", "/cart/showLike", "/good/rag-search", "/usr/login-control", "/catalog/images/test.jpg"})
        public String handle() { calls.incrementAndGet(); return "ok"; }
    }
}
