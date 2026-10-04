# ADR-006: 限流演算法抽成介面，由設定選擇實作

狀態：已採納

## 背景

Stage 2 的 fixed window 要換成 token bucket。原本的 `FixedWindowRateLimitFilter`
把三件事混在同一個類別：從 request 取出 client key、判斷要不要放行、寫 header 與 429 回應。
其中第一和第三件不管用什麼演算法都一樣。

直接改掉的話，Stage 5 換成 Redis 版時要把同樣的搬移工作再做一次。

## 決定

抽出單一方法的 `RateLimiter` 介面，由 `ratelimit.orders.algorithm` 搭配
`@ConditionalOnProperty` 選擇實作。

```java
public interface RateLimiter {
    Decision tryAcquire(String key);
    record Decision(boolean allowed, long remaining, Duration retryAfter, long limit) {}
}
```

`RateLimitFilter` 只負責 HTTP，對演算法一無所知。

## 理由

抽介面在多數情況下是過度設計 —— 為想像中的彈性付出間接層的成本。這次不是，有兩個具體依據：

1. **現在手上就有兩個實作，第三個（Redis）已經排進計畫。**
   判斷依據是「現在有幾個」，不是「未來可能有幾個」
2. **比較演算法是這個專案的目的。** Stage 9 要畫不同演算法的流量曲線，
   能用一行設定切換，就不必每次改程式碼重新建置

介面只有一個方法，刻意不加 `reset()`、`getConfig()` 這些目前沒人用的東西。
`limit` 放進 `Decision` 而不是獨立的 `limit()` 方法，是因為它必須和 `remaining` 一致，
而且之後若支援每租戶動態配額，配額會變成 per-key，獨立方法就不夠用。

## 取捨

- 多一層間接。讀程式碼的人要多跳一次才看得到實際邏輯
- **`algorithm` 這個值同時存在兩個地方**：`RateLimitProperties` 裡一份、
  `@ConditionalOnProperty` 裡一份。無法避免 —— 條件判斷發生在 bean 定義階段，
  那時 `@ConfigurationProperties` 還沒綁定完成。緩解方式是每個實作在
  `@PostConstruct` 印出自己的設定，讓「設定寫 A 卻載入 B」立刻現形
- `Decision` 的四個欄位有三個是數字，傳錯位置編譯器抓不到。
  欄位再增加的話應該改用具名的靜態工廠方法

## 後續

Stage 5 的 Redis 實作直接新增第三個 `RateLimiter`，`RateLimitFilter` 不需要改動 ——
這是這個決定是否正確的驗證點。如果屆時發現介面得為了 Redis 而改簽章，
代表接縫切錯位置了。
