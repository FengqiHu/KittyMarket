package com.example.maoliang.Config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.rate-limit")
public record RateLimitProperties(boolean enabled, String trustedProxy, Bucket general,
                                  Bucket write, Bucket ai, Bucket auth) {
    public record Bucket(int capacity, double refillPerSecond) {
        public Bucket {
            if (capacity < 1 || !Double.isFinite(refillPerSecond) || refillPerSecond <= 0) {
                throw new IllegalArgumentException("Rate limit capacity and refill rate must be positive");
            }
        }
        public long ttlMillis() { return (long) Math.ceil(capacity / refillPerSecond * 1000) + 1000; }
    }
}
