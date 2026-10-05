# ADR-008: Redis 不可用時 fail-open

狀態：已採納

## 背景

ADR-007 把限流狀態移到 Redis，代價是「多一個必須存活的元件」，並留下一個未回答的問題：
Redis 連不上時限流器該怎麼辦。

第一版沒有處理，所以行為是**第三種**：`RedisConnectionFailureException` 直接冒出去，
請求變成 500。那不是一個有意識的選擇，是遺漏 —— 而且它的效果等同 fail-closed，
卻連一個像樣的錯誤訊息都沒有。

## 決定

**fail-open** —— Redis 不可用時放行，並記 WARN log。

只攔「可用性」類的例外：

```java
catch (RedisConnectionFailureException | QueryTimeoutException e) {
    log.warn("Redis unavailable, failing open for key={}: {}", key, e.getMessage());
    return new Decision(true, capacity, Duration.ZERO, capacity);
}
```

**刻意不攔 `RedisSystemException`。** 那代表 Lua 腳本本身有錯，是程式 bug。
對它 fail-open 會讓限流靜默失效 —— 腳本寫壞之後所有請求都通過，而且沒有人會發現。
那類例外必須冒成 500。

## 理由

| | fail-open | fail-closed |
|---|---|---|
| Redis 掛掉 | 限流失效，下游可能被打爆 | 服務完全不可用 |
| 適合 | **保護性**限流 | 配額 / 計費型限流 |
| 風險 | 故障被放大 | 自己造成故障 |

這個專案的限流是保護性的，不涉及付費配額。核心判準是：

> **限流器自己故障，不應該比它要防的問題更嚴重。**

Redis 掛掉的同時流量未必在攻擊高峰。fail-closed 等於「每次 Redis 抖一下，整個服務就 100% 不可用」——
把一個中介元件的故障放大成全站故障。fail-open 的最壞情況是「回到沒有限流的狀態」，
那是上線前的狀態，服務至少還能運作。

被放棄的選項：**降級成本機桶**（Redis 掛掉時退回記憶體版的 token bucket）。
它在兩者之間取得平衡 —— 上限變成「設定值 × 實例數」，但至少還有保護。
現在不做是因為它需要在 `RedisRateLimiter` 裡持有一個 `Bucket4jRateLimiter`
並處理兩者的狀態切換，複雜度明顯高於目前的收益。

## 取捨

- **Redis 掛掉期間完全沒有限流保護。** 如果剛好遇上攻擊，下游會直接承受
- **log 會洪水般湧出。** 每個請求一筆 WARN，Redis 掛十分鐘就是數十萬筆。
  正式環境應該對這筆 log 本身做節流（例如只記第一次 + 每分鐘一次摘要），
  或改用 metrics counter 計數、log 只記狀態轉換
- fail-open 的次數應該是一個**告警指標**，不是只記在 log 裡。Stage 9 接 metrics 時要補上

## 後續

Stage 9 加入 Micrometer 之後，為 fail-open 加一個 counter 並設告警 ——
「限流器正在 fail-open」是運維必須立刻知道的事。

若之後限流改成承載付費配額（延伸題的「每租戶動態配額」），
這個決定必須重新評估：那時候放行等於免費送，應改為 fail-closed 或降級成本機桶。
