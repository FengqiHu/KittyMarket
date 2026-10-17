package com.example.maoliang.Service;

import com.example.maoliang.Entity.Good;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class HotGoodsCacheTests {
    StringRedisTemplate redis;
    ValueOperations<String, String> values;
    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    HotGoodsCache cache;
    Supplier<List<Good>> loader;
    List<Good> goods;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setup() {
        redis = mock(StringRedisTemplate.class);
        values = mock(ValueOperations.class);
        loader = mock(Supplier.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.get(HotGoodsCache.GENERATION_KEY)).thenReturn("initial-generation");
        Good good = new Good();
        good.setGoodid(42);
        good.setGoodname("测试商品");
        good.setPrice(19.9);
        good.setNumber(5);
        goods = List.of(good);
        when(loader.get()).thenReturn(goods);
        cache = new HotGoodsCache(redis, mapper, true, Duration.ofMinutes(5));
    }

    @AfterEach
    void cleanup() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void cacheMissWritesJsonWithTtlAndGeneration() throws Exception {
        assertEquals(goods, cache.getOrLoad(loader));
        verify(redis).execute(any(DefaultRedisScript.class), eq(List.of(HotGoodsCache.KEY, HotGoodsCache.GENERATION_KEY)),
                eq("initial-generation"), eq(mapper.writeValueAsString(goods)), eq("300000"));
        verify(loader).get();
    }

    @Test
    void hitAvoidsDatabaseAndReturnsIndependentObjects() throws Exception {
        when(values.get(HotGoodsCache.KEY)).thenReturn(mapper.writeValueAsString(goods));
        List<Good> first = cache.getOrLoad(loader);
        first.get(0).setPrice(999);
        assertEquals(19.9, cache.getOrLoad(loader).get(0).getPrice());
        verifyNoInteractions(loader);
    }

    @Test
    void corruptedOrNullJsonReloadsDatabase() {
        when(values.get(HotGoodsCache.KEY)).thenReturn("{broken", "null");
        assertEquals(goods, cache.getOrLoad(loader));
        assertEquals(goods, cache.getOrLoad(loader));
        verify(loader, times(2)).get();
    }

    @Test
    void unavailableRedisFallsBackAndTemporarilySkipsRetries() {
        when(values.get(HotGoodsCache.GENERATION_KEY)).thenThrow(new RedisConnectionFailureException("offline"));
        assertEquals(goods, cache.getOrLoad(loader));
        assertEquals(goods, cache.getOrLoad(loader));
        verify(values, times(1)).get(HotGoodsCache.GENERATION_KEY);
        verify(loader, times(2)).get();
    }

    @Test
    void cacheWriteFailureDoesNotLoseDatabaseResults() {
        when(redis.execute(any(DefaultRedisScript.class), anyList(), any(), any(), any()))
                .thenThrow(new RedisConnectionFailureException("offline"));
        assertEquals(goods, cache.getOrLoad(loader));
    }

    @Test
    void databaseFailurePropagatesWithoutCaching() {
        when(loader.get()).thenThrow(new IllegalStateException("database unavailable"));
        assertThrows(IllegalStateException.class, () -> cache.getOrLoad(loader));
        verify(redis, never()).execute(any(DefaultRedisScript.class), anyList(), any(), any(), any());
    }

    @Test
    void disabledCacheUsesOnlyDatabase() {
        cache = new HotGoodsCache(redis, mapper, false, Duration.ofMinutes(5));
        assertEquals(goods, cache.getOrLoad(loader));
        cache.invalidateAfterCommit();
        verifyNoInteractions(redis);
    }

    @Test
    void evictionWaitsForCommit() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        cache.invalidateAfterCommit();
        verifyNoInteractions(redis);
        TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());
        verify(redis).delete(List.of(HotGoodsCache.KEY, HotGoodsCache.GENERATION_KEY));
    }

    @Test
    void rollbackKeepsCache() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        cache.invalidateAfterCommit();
        TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCompletion(1));
        verifyNoInteractions(redis);
    }

    @Test
    void evictionFailureDoesNotFailSuccessfulMutation() {
        when(redis.delete(anyCollection())).thenThrow(new RedisConnectionFailureException("offline"));
        assertDoesNotThrow(cache::invalidateAfterCommit);
    }
}
