package com.willie.ratelimit.order.adapter.in.web.ratelimit.impl;

import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import com.willie.ratelimit.order.adapter.in.web.ratelimit.RateLimitProperties;
import com.willie.ratelimit.order.adapter.in.web.ratelimit.RateLimiter;

import jakarta.annotation.PostConstruct;

@Component 
@ConditionalOnProperty (prefix = "ratelimit.orders" ,name = "algorithm" ,havingValue ="redis-token-bucket")
public class RedisRateLimiter implements RateLimiter{

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);
    private static final String KEY_PREFIX = "ratelimit:orders:";
    private final StringRedisTemplate redisTemplate;
    private final RateLimitProperties properties;
    private static final RedisScript<List> TOKEN_BUCKET = RedisScript.of(
                                    new ClassPathResource("scripts/token_bucket.lua"),
                                    List.class);
    
    public RedisRateLimiter(StringRedisTemplate redisTemplate ,RateLimitProperties properties){
        this.properties = properties;
        this.redisTemplate = redisTemplate;
    }
    @PostConstruct
    void logActiveAlgorithm() {
        var tb = properties.tokenBucket();
        log.info("Rate limiter: redis-token-bucket (capacity={}, refill={} per {}, failure policy=fail-open)",
                tb.capacity(), tb.refillTokens(), tb.refillPeriod());
    }

    @Override
    public Decision tryAcquire(String key) {
        var tokenBucket = properties.tokenBucket();
        int capacity = tokenBucket.capacity();
        int refillToken = tokenBucket.refillTokens();
        var refillPeriod = tokenBucket.refillPeriod();

        double ratePerMs = (double)refillToken /refillPeriod.toMillis();
        // TTL = 從空到滿需要多久。閒置超過這段時間的 key，桶子本來就該是滿的，
        // 讓它過期消失跟留著的結果一樣
        long ttlMs = (long) Math.ceil(capacity / ratePerMs);
        List result;
        try {
            result = redisTemplate.execute(TOKEN_BUCKET,
                    List.of(KEY_PREFIX + key),
                    String.valueOf(capacity),
                    String.valueOf(ratePerMs),
                    String.valueOf(ttlMs));
        } catch (RedisConnectionFailureException | QueryTimeoutException e) {
            // fail-open：Redis 不可用時放行。限流是保護性措施，
            // 限流器自己故障不應該比它要防的問題更嚴重（見 ADR-008）。
            //
            // 刻意【不】攔 RedisSystemException —— 那代表 Lua 腳本有錯，
            // 是程式 bug。對它 fail-open 會讓限流靜默失效，必須讓它冒成 500。
            log.warn("Redis unavailable, failing open for key={}: {}", key, e.getMessage());
            return new Decision(true, capacity, Duration.ZERO, capacity);
        }

        long allowed   = ((Number) result.get(0)).longValue();
        long remaining = ((Number) result.get(1)).longValue();
        long retryMs   = ((Number) result.get(2)).longValue();

        return new Decision(allowed == 1L, remaining, Duration.ofMillis(Math.max(1, retryMs)), capacity);
    }

}
