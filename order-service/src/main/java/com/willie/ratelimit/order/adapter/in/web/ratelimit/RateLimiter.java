package com.willie.ratelimit.order.adapter.in.web.ratelimit;

import java.time.Duration;

public interface RateLimiter {
    Decision tryAcquire(String key);

    record Decision(boolean allowed, long remaining, Duration retryAfter, long limit) {
    }
}
