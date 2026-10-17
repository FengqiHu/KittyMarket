package com.example.maoliang.Service;

import com.example.maoliang.Entity.Good;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

@Component
public class HotGoodsCache {
    public static final String KEY = "kittymarket:{hot-goods}:v1";
    public static final String GENERATION_KEY = KEY + ":generation";
    private static final Logger LOGGER = LoggerFactory.getLogger(HotGoodsCache.class);
    private static final TypeReference<List<Good>> GOODS_TYPE = new TypeReference<>() {};
    // A reader that started before a committed update must not repopulate stale data.
    private static final DefaultRedisScript<Long> PUT_IF_CURRENT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[2]) == ARGV[1] then
                redis.call('SET', KEYS[1], ARGV[2], 'PX', ARGV[3])
                return 1
            end
            return 0
            """, Long.class);
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final boolean enabled;
    private final Duration ttl;
    private volatile long retryAfterNanos;

    public HotGoodsCache(StringRedisTemplate redis, ObjectMapper mapper,
                        @Value("${app.cache.hot-goods.enabled:true}") boolean enabled,
                        @Value("${app.cache.hot-goods.ttl:5m}") Duration ttl) {
        if (ttl.toMillis() < 1) throw new IllegalArgumentException("Hot goods TTL must be at least 1ms");
        this.redis = redis;
        this.mapper = mapper;
        this.enabled = enabled;
        this.ttl = ttl;
    }

    public List<Good> getOrLoad(Supplier<List<Good>> loader) {
        if (!enabled || System.nanoTime() < retryAfterNanos) return loader.get();
        String generation;
        try {
            generation = redis.opsForValue().get(GENERATION_KEY);
            if (generation == null) {
                // Random generations cannot be reused even if Redis evicts the version key.
                redis.opsForValue().setIfAbsent(GENERATION_KEY, UUID.randomUUID().toString());
                generation = redis.opsForValue().get(GENERATION_KEY);
            }
            String cached = redis.opsForValue().get(KEY);
            if (cached != null) {
                try {
                    List<Good> goods = mapper.readValue(cached, GOODS_TYPE);
                    if (goods != null) return goods;
                } catch (JsonProcessingException exception) {
                    LOGGER.warn("Invalid hot goods cache JSON; reloading from MySQL");
                }
            }
        } catch (RuntimeException exception) {
            unavailable(exception);
            return loader.get();
        }

        // Database failures propagate normally; only cache failures are tolerated.
        List<Good> goods = loader.get();
        try {
            if (generation != null) {
                redis.execute(PUT_IF_CURRENT, List.of(KEY, GENERATION_KEY), generation,
                        mapper.writeValueAsString(goods), Long.toString(ttl.toMillis()));
            }
        } catch (JsonProcessingException exception) {
            LOGGER.warn("Cannot serialize hot goods; returning MySQL results");
        } catch (RuntimeException exception) {
            unavailable(exception);
        }
        return goods;
    }

    public void invalidateAfterCommit() {
        if (!enabled) return;
        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() { invalidate(); }
            });
        } else {
            invalidate();
        }
    }

    private void invalidate() {
        try {
            // DEL of both keys is atomic; any in-flight reader retains an obsolete generation.
            redis.delete(List.of(KEY, GENERATION_KEY));
            retryAfterNanos = 0;
        } catch (RuntimeException exception) {
            unavailable(exception);
        }
    }

    private void unavailable(RuntimeException exception) {
        long now = System.nanoTime();
        if (now >= retryAfterNanos) {
            LOGGER.warn("Redis hot goods cache unavailable; using MySQL for 10 seconds ({})",
                    exception.getClass().getSimpleName());
        }
        retryAfterNanos = now + Duration.ofSeconds(10).toNanos();
    }
}
