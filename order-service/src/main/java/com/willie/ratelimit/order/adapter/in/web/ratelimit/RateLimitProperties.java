package com.willie.ratelimit.order.adapter.in.web.ratelimit;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 對應 application.yml 的 ratelimit.orders.*
 *
 * ignoreUnknownFields = false：yml 在這個 prefix 底下打錯字會讓應用啟動失敗，
 *
 * @param enabled 關掉就完全不限流，方便壓測時比較
 * @param limit   一個 window 內允許幾次請求
 * @param window  一個 window 多長（1m / 30s / 500ms 都可以）
 */
@Validated
@ConfigurationProperties(prefix = "ratelimit.orders", ignoreUnknownFields = false)
public record RateLimitProperties(
        @DefaultValue("true") boolean enabled,
        @Positive @DefaultValue("20") int limit,
        @NotNull @DefaultValue("1m") Duration window) {
}
