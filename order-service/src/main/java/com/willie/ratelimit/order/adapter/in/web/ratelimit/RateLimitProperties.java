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
 * ignoreUnknownFields = false：這個 prefix 底下打錯字會讓應用啟動失敗，
 * 而不是靜默套用預設值。
 *
 * @param enabled     關掉就完全不限流，方便壓測時比較
 * @param algorithm   fixed-window | token-bucket，決定載入哪個 RateLimiter 實作
 * @param fixedWindow fixed-window 演算法的參數
 * @param tokenBucket token-bucket 演算法的參數
 */
@Validated
@ConfigurationProperties(prefix = "ratelimit.orders", ignoreUnknownFields = false)
public record RateLimitProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue("token-bucket") String algorithm,
        @DefaultValue FixedWindow fixedWindow,
        @DefaultValue TokenBucket tokenBucket) {

    public record FixedWindow(
            @Positive @DefaultValue("20") int limit,
            @NotNull  @DefaultValue("1m") Duration window) {}

    public record TokenBucket(
            @Positive @DefaultValue("20") int capacity,
            @Positive @DefaultValue("20") int refillTokens,
            @NotNull  @DefaultValue("1m") Duration refillPeriod) {}

}
