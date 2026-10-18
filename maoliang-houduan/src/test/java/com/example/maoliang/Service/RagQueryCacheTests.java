package com.example.maoliang.Service;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class RagQueryCacheTests {
    @Test
    void identicalConcurrentQueriesShareOneLoader() throws Exception {
        var cache = new RagQueryCache<String,Integer>(2, Duration.ofMinutes(1));
        var calls = new AtomicInteger();
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> cache.get("same",() -> {
                calls.incrementAndGet(); entered.countDown();
                try { assertTrue(release.await(5, TimeUnit.SECONDS)); }
                catch(InterruptedException e) { throw new IllegalStateException(e); }
                return 7;
            }));
            assertTrue(entered.await(5,TimeUnit.SECONDS));
            var second = executor.submit(() -> cache.get("same",() -> { calls.incrementAndGet(); return 9; }));
            release.countDown();
            assertEquals(7,first.get(5,TimeUnit.SECONDS));
            assertEquals(7,second.get(5,TimeUnit.SECONDS));
            assertEquals(1,calls.get());
        } finally { release.countDown(); executor.shutdownNow(); }
    }

    @Test
    void expiredFailedAndEvictedEntriesCanBeLoadedAgain() {
        var calls = new AtomicInteger();
        var expired = new RagQueryCache<String,Integer>(1,Duration.ZERO);
        assertEquals(1,expired.get("a",calls::incrementAndGet));
        assertEquals(2,expired.get("a",calls::incrementAndGet));
        var cache = new RagQueryCache<String,Integer>(1,Duration.ofMinutes(1));
        assertThrows(IllegalStateException.class,() -> cache.get("failure",() -> {throw new IllegalStateException();}));
        assertEquals(3,cache.get("failure",calls::incrementAndGet));
        cache.get("other",calls::incrementAndGet);
        assertEquals(5,cache.get("failure",calls::incrementAndGet));
    }
}
