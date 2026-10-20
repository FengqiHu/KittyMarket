package com.example.maoliang.Service;

import com.example.maoliang.Config.RateLimitProperties.Bucket;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RequestRateLimiterTests {
    @Test
    void concurrentBurstsCannotExceedCapacityEvenWhenRedisIsUnavailable() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenThrow(new RedisConnectionFailureException("offline"));
        MutableClock clock = new MutableClock();
        var limiter = new RequestRateLimiter(redis, clock);
        var policy = new Bucket(5, 1);
        AtomicInteger admitted = new AtomicInteger();
        var pool = Executors.newFixedThreadPool(12);
        try {
            var tasks = java.util.stream.IntStream.range(0, 100).mapToObj(i -> pool.submit(() -> {
                if (limiter.acquire("same-user", "write", policy) == 0) admitted.incrementAndGet();
            })).toList();
            for (var task : tasks) task.get();
            assertEquals(5, admitted.get());
            assertTrue(limiter.acquire("same-user", "write", policy) > 0);
            assertEquals(0, limiter.acquire("another-user", "write", policy));
            clock.time.addAndGet(1000);
            assertEquals(0, limiter.acquire("same-user", "write", policy));
            assertTrue(limiter.acquire("same-user", "write", policy) > 0);
        } finally { pool.shutdownNow(); }
    }

    @Test
    void fallbackRetainsSpentTokensAndBackwardTimeCannotMintNewTokens() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .thenReturn(0L).thenThrow(new RedisConnectionFailureException("offline"));
        MutableClock clock = new MutableClock();
        var limiter = new RequestRateLimiter(redis, clock);
        var policy = new Bucket(2, 1);
        assertEquals(0, limiter.acquire("user", "all", policy));
        assertEquals(0, limiter.acquire("user", "all", policy));
        assertEquals(1000, limiter.acquire("user", "all", policy));
        clock.time.addAndGet(-5000);
        assertTrue(limiter.acquire("user", "all", policy) > 0);
        clock.time.addAndGet(6000);
        assertEquals(0, limiter.acquire("user", "all", policy));
    }

    private static class MutableClock extends Clock {
        final AtomicLong time = new AtomicLong(100_000);
        public ZoneId getZone() { return ZoneId.of("UTC"); }
        public Clock withZone(ZoneId zone) { return this; }
        public Instant instant() { return Instant.ofEpochMilli(time.get()); }
    }
}
