package com.willie.ratelimit.order.adapter.in.web.ratelimit.impl;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.willie.ratelimit.order.adapter.in.web.ratelimit.RateLimitProperties;
import com.willie.ratelimit.order.adapter.in.web.ratelimit.RateLimiter;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.TimeMeter;

import jakarta.annotation.PostConstruct;

@Component
@ConditionalOnProperty(prefix = "ratelimit.orders", name = "algorithm", havingValue = "token-bucket")
public class Bucket4jRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(Bucket4jRateLimiter.class);

    private final RateLimitProperties properties;

    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    private final Clock clock;

    public Bucket4jRateLimiter(RateLimitProperties properties, Clock clock) {
        this.clock = clock;
        this.properties = properties;
    }

    @PostConstruct
    void logActiveAlgorithm() {
        var tb = properties.tokenBucket();
        log.info("Rate limiter: token-bucket (capacity={}, refill={} per {})",
                tb.capacity(), tb.refillTokens(), tb.refillPeriod());
    }

    @Override
    public Decision tryAcquire(String key) {

        var bucket = buckets.computeIfAbsent(key , k ->{
            var tokenBucket = properties.tokenBucket();
            var limit = Bandwidth.builder()
            .capacity(tokenBucket.capacity())
            .refillGreedy(tokenBucket.refillTokens(), tokenBucket.refillPeriod())
            .build();
            
            return Bucket.builder()
            .addLimit(limit)
            .withCustomTimePrecision(new TimeMeter() {
                @Override public long currentTimeNanos() { return clock.millis() * 1_000_000L; }
                @Override public boolean isWallClockBased() { return true; }
            })
            .build();
        });
        ConsumptionProbe result = bucket.tryConsumeAndReturnRemaining(1);
        boolean allowed = result.isConsumed();
        long retryAfterSeconds = allowed ? 0 : result.getNanosToWaitForRefill() / 1_000_000_000;
        return new Decision(allowed, result.getRemainingTokens(), Duration.ofSeconds(retryAfterSeconds),  properties.tokenBucket().capacity());
    }

}
