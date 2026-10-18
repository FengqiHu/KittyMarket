package com.example.maoliang.Service;

import java.time.Duration;
import java.util.Comparator;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Bounded, expiring cache that shares one in-flight request for identical keys. */
final class RagQueryCache<K, V> {
    private record Entry<V>(long created, CompletableFuture<V> value) {}
    private final ConcurrentHashMap<K, Entry<V>> entries = new ConcurrentHashMap<>();
    private final long ttlNanos;
    private final int capacity;

    RagQueryCache(int capacity, Duration ttl) { this.capacity = capacity; this.ttlNanos = ttl.toNanos(); }

    V get(K key, Supplier<V> loader) {
        for (;;) {
            long now = System.nanoTime();
            Entry<V> old = entries.get(key);
            if (old != null) {
                if (!old.value().isDone() || now - old.created() < ttlNanos) return await(old.value());
                entries.remove(key, old);
                continue;
            }
            Entry<V> next = new Entry<>(now, new CompletableFuture<>());
            if (entries.putIfAbsent(key, next) != null) continue;
            try {
                V value = loader.get();
                next.value().complete(value);
                evict();
                return value;
            } catch (RuntimeException | Error e) {
                next.value().completeExceptionally(e);
                entries.remove(key, next);
                throw e;
            }
        }
    }

    private V await(CompletableFuture<V> value) {
        try { return value.join(); }
        catch (CompletionException e) {
            if (e.getCause() instanceof RuntimeException cause) throw cause;
            throw e;
        }
    }

    private void evict() {
        // Never evict in-flight work and accidentally duplicate an external call.
        entries.entrySet().stream().filter(entry -> entry.getValue().value().isDone())
                .sorted(Comparator.comparingLong(entry -> entry.getValue().created()))
                .limit(Math.max(0, entries.size() - capacity))
                .forEach(entry -> entries.remove(entry.getKey(), entry.getValue()));
    }
}
