package com.willie.ratelimit.order.adapter.in.web.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import com.willie.ratelimit.order.adapter.in.web.ratelimit.RateLimiter.Decision;
import com.willie.ratelimit.order.adapter.in.web.ratelimit.impl.RedisRateLimiter;

/**
 * 跑在真的 Redis 上（Testcontainers 起一顆），因為整個演算法在 Lua 裡，
 * 沒有辦法用假物件替代。
 *
 * 這支測試【不驗補充行為】—— 時間由 Redis 的 TIME 決定，@MockitoBean Clock 影響不到它。
 * 要驗補充只能 Thread.sleep（慢又不穩）或把 TIME 參數化（會摧毀「Redis 是唯一時間來源」
 * 這個設計目的）。補充的正確性已經由 Bucket4jRateLimiterTest 用可控時鐘驗過，
 * 演算法相同，差別只在狀態存哪裡。
 *
 * 這支要驗的是記憶體版做不到的那件事：【多個實例共用同一份額度】。
 *
 * 容器是 static，整個類別共用一顆，所以 Redis 狀態會在測試方法之間殘留。
 * 慣例：每個測試方法用自己的 key，不要共用。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@TestPropertySource(properties = {
        "ratelimit.orders.enabled=true",
        "ratelimit.orders.algorithm=redis-token-bucket",
        "ratelimit.orders.token-bucket.capacity=5",
        "ratelimit.orders.token-bucket.refill-tokens=5",
        "ratelimit.orders.token-bucket.refill-period=1m"
})
        // 1. twoInstancesShareOneQuota            key: "shared-quota"      ★ 核心
        //    new RedisRateLimiter(redisTemplate, properties) 做出第二個實例，
        //    兩邊加起來只能通過 capacity 次。這是記憶體版做不到的事。
        //
        // 2. requestBeyondCapacityIsRejected      key: "beyond-capacity"
        //    打滿 5 次後第 6 次 allowed=false，retryAfter 大於零。
        //
        // 3. differentKeysGetTheirOwnBucket       key: "own-bucket-a" / "own-bucket-b"
        //    一個 key 用光不影響另一個。
        //
        // 4. stateIsActuallyStoredInRedis         key: "stored-in-redis"
        //    呼叫之後 redisTemplate.opsForHash() 讀得到 tokens 與 ts。

class RedisRateLimiterTest {

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    /**
     * 容器的 6379 會對應到宿主機的隨機 port（避開本機既有的 Redis），
     * 所以設定值要等容器啟動後才知道 —— 用 lambda 延遲求值。
     */
    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private RateLimitProperties properties;

    @Autowired
    private RedisRateLimiter rateLimiter;

    @Test
    void twoInstancesShareOneQuota(){
        RedisRateLimiter secondLimiter = new RedisRateLimiter(redisTemplate, properties);

        for( int i =0; i < 2 ; i++ ) {
            assertThat(
                rateLimiter.tryAcquire("shared-quota").allowed()
            ).isTrue();
        }
        for( int i = 0; i < 3 ; i++) {
            assertThat(
                secondLimiter.tryAcquire("shared-quota").allowed()
            ).isTrue();
        }
        assertThat(secondLimiter.tryAcquire("shared-quota").allowed()).isFalse();
        assertThat(rateLimiter.tryAcquire("shared-quota").allowed()).isFalse();
    }
    @Test
    void requestBeyondCapacityIsRejected(){
        for( int i =0; i < 5 ; i++ ) {
            assertThat(
                rateLimiter.tryAcquire("beyond-capacity").allowed()
            ).isTrue();
        }
        Decision result  = rateLimiter.tryAcquire("beyond-capacity");
        assertThat(result.allowed()).isFalse();
        assertThat(result.remaining() ).isZero();
        assertThat(result.retryAfter()).isPositive();
    }
    @Test
    void differentKeysGetTheirOwnBucket(){
        for( int i =0; i < 5 ; i++ ) {
            assertThat(
                rateLimiter.tryAcquire("own-bucket-a").allowed()
            ).isTrue();
        }
        for( int i = 0; i < 5 ; i++) {
            assertThat(
                rateLimiter.tryAcquire("own-bucket-b").allowed()
            ).isTrue();
        }
        Decision resultA  = rateLimiter.tryAcquire("own-bucket-a");
        assertThat(resultA.allowed()).isFalse();
        assertThat(resultA.remaining() ).isZero();
        assertThat(resultA.retryAfter()).isPositive();
        Decision resultB  = rateLimiter.tryAcquire("own-bucket-b");
        assertThat(resultB.allowed()).isFalse();
        assertThat(resultB.remaining() ).isZero();
        assertThat(resultB.retryAfter()).isPositive();
        

    }
    @Test
    void stateIsActuallyStoredInRedis(){
        rateLimiter.tryAcquire("stored-in-redis");
        var hash = redisTemplate.opsForHash().entries("ratelimit:orders:stored-in-redis");
        assertThat(hash).containsKeys("tokens","ts");
        assertThat(Double.parseDouble((String) hash.get("tokens"))).isEqualTo(4.0, within(0.1));
        assertThat(redisTemplate.getExpire("ratelimit:orders:stored-in-redis")).isPositive();
    }

    /**
     * fail-open：Redis 不可用時放行（ADR-008）。
     *
     * 用 mock 讓 execute 丟連線例外，不用真的把容器關掉 —— 關掉會影響同類別其他測試，
     * 而且等連線逾時會讓測試變慢。
     */
    @Test
    void failsOpenWhenRedisIsUnavailable() {
        StringRedisTemplate unreachable = mock(StringRedisTemplate.class);
        given(unreachable.execute(any(RedisScript.class), anyList(), any(Object[].class)))
                .willThrow(new RedisConnectionFailureException("redis is down"));

        var limiter = new RedisRateLimiter(unreachable, properties);

        // 連打超過 capacity 次都要放行 —— 限流器故障不該讓服務跟著不可用
        for (int i = 0; i < properties.tokenBucket().capacity() + 5; i++) {
            assertThat(limiter.tryAcquire("fail-open").allowed()).isTrue();
        }
    }
}
