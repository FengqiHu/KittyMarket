package com.example.maoliang.Service;

import com.example.maoliang.Config.RateLimitProperties.Bucket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RequestRateLimiter {
    private static final Logger LOG = LoggerFactory.getLogger(RequestRateLimiter.class);
    private static final int MAX_LOCAL_BUCKETS = 10_000;
    private static final DefaultRedisScript<Long> TAKE = new DefaultRedisScript<>("""
            local time = redis.call('TIME')
            local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)
            local capacity = tonumber(ARGV[1])
            local rate = tonumber(ARGV[2]) / 1000
            local state = redis.call('HMGET', KEYS[1], 'tokens', 'updated')
            local tokens = tonumber(state[1]) or capacity
            local updated = tonumber(state[2]) or now
            now = math.max(now, updated)
            tokens = math.min(capacity, tokens + (now - updated) * rate)
            local wait = 0
            if tokens >= 1 then
                tokens = tokens - 1
            else
                wait = math.ceil((1 - tokens) / rate)
            end
            redis.call('HSET', KEYS[1], 'tokens', tokens, 'updated', now)
            redis.call('PEXPIRE', KEYS[1], ARGV[3])
            return wait
            """, Long.class);

    private final StringRedisTemplate redis;
    private final Clock clock;
    private final Map<String, LocalBucket> local = new HashMap<>();
    private volatile long redisRetryAt;
    private long nextCleanup;

    @Autowired
    public RequestRateLimiter(StringRedisTemplate redis) { this(redis, Clock.systemUTC()); }
    RequestRateLimiter(StringRedisTemplate redis, Clock clock) { this.redis = redis; this.clock = clock; }

    /** Returns milliseconds until another request may be made, or zero if admitted. */
    public long acquire(String identity, String group, Bucket policy) {
        String key = "kittymarket:rate-limit:v1:{" + identity + "}:" + group;
        // Keep the local budget warm so a Redis outage cannot reset a caller's burst allowance.
        long localWait = acquireLocal(key, policy);
        if (localWait > 0) return localWait;
        long now = clock.millis();
        if (now < redisRetryAt) return 0;
        try {
            Long wait = redis.execute(TAKE, List.of(key), Integer.toString(policy.capacity()),
                    Double.toString(policy.refillPerSecond()), Long.toString(policy.ttlMillis()));
            if (wait == null) throw new IllegalStateException("Redis returned no rate limit decision");
            return wait;
        } catch (RuntimeException error) {
            if (now >= redisRetryAt) LOG.warn("Redis rate limiter unavailable; using bounded local limits for 10 seconds ({})", error.getClass().getSimpleName());
            redisRetryAt = now + 10_000;
            return 0;
        }
    }

    private synchronized long acquireLocal(String key, Bucket policy) {
        long now = clock.millis();
        if (now >= nextCleanup) {
            local.values().removeIf(bucket -> bucket.expiresAt <= now);
            nextCleanup = now + 60_000;
        }
        LocalBucket bucket = local.get(key);
        if (bucket == null) {
            if (local.size() >= MAX_LOCAL_BUCKETS) return 1000;
            bucket = new LocalBucket(policy.capacity(), now);
            local.put(key, bucket);
        }
        long effectiveNow = Math.max(now, bucket.updated);
        bucket.tokens = Math.min(policy.capacity(), bucket.tokens + (effectiveNow - bucket.updated) * policy.refillPerSecond() / 1000);
        bucket.updated = effectiveNow;
        bucket.expiresAt = effectiveNow + policy.ttlMillis();
        if (bucket.tokens < 1) return (long) Math.ceil((1 - bucket.tokens) / policy.refillPerSecond() * 1000);
        bucket.tokens -= 1;
        return 0;
    }

    private static class LocalBucket {
        double tokens;
        long updated;
        long expiresAt;
        LocalBucket(double tokens, long updated) { this.tokens = tokens; this.updated = updated; }
    }
}
