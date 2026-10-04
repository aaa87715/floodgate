package com.willie.ratelimit.order.adapter.in.web.ratelimit.impl;


import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import org.springframework.stereotype.Component;

import com.willie.ratelimit.order.adapter.in.web.ratelimit.RateLimiter;

import jakarta.annotation.PostConstruct;
import com.willie.ratelimit.order.adapter.in.web.ratelimit.RateLimitProperties;
import com.willie.ratelimit.order.adapter.in.web.ratelimit.RateLimitProperties.FixedWindow;

@Component 
@ConditionalOnProperty(prefix = "ratelimit.orders", name = "algorithm", havingValue = "fixed-window")
public class FixedWindowRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(FixedWindowRateLimiter.class);

    private final Clock clock;
    private final Map<String, AtomicReference<Window>> windows = new ConcurrentHashMap<>();
    private final RateLimitProperties properties;
    
    private record Window(long startMillis, int count ) {

    }
    public FixedWindowRateLimiter(Clock clock, RateLimitProperties properties) {
        this.clock = clock;
        this.properties = properties;
    }

    /** 設定與實際載入的實作不一致時，這一行會立刻讓你看見。 */
    @PostConstruct
    void logActiveAlgorithm() {
        FixedWindow fw = properties.fixedWindow();
        log.info("Rate limiter: fixed-window (limit={}, window={})", fw.limit(), fw.window());
    }

    @Override
    public Decision tryAcquire(String key) {
        long now = clock.millis();
        FixedWindow property = properties.fixedWindow();
        long windowMillis = property.window().toMillis();
        int limit = property.limit();
        var ref = windows.computeIfAbsent(key , k -> new AtomicReference<>(new Window(now, 0)));
        Window  currentWindow = ref.updateAndGet(window -> {
            if( now - window.startMillis() >= windowMillis) {
                return new Window(now , 1);
            }
            return new Window(window.startMillis(), window.count() + 1);
        });

        boolean allowed = currentWindow.count() <= limit;
        long windowEnd = currentWindow.startMillis() + windowMillis;
        int remaining = allowed ? limit -  currentWindow.count() : 0;
        return new Decision(allowed, remaining, Duration.ofSeconds(Math.max(1, (windowEnd - now) / 1000)),  property.limit());
    }




}
