# ADR-005: 限流放在哪一層

狀態：已採納

## 背景

這個架構有兩個可以放限流的位置：api-gateway（邊界）和 order-service（服務內）。
常見的說法是「defense in depth，兩層都要」。

## 決定

**正式環境以 gateway 為主力，服務內不放限流器。**
服務自保改用連線池上限、逾時、Resilience4j 的 Bulkhead 與 CircuitBreaker。

這個專案的 Stage 2 到 Stage 5 在 order-service 內實作限流，是**刻意的教學用途**，
Stage 6 之後那一層會退場。

## 理由

「每個服務都自己寫一個限流器」不是業界常態，多數情況是過度設計。真實系統的分層是：

| 層 | 普及度 |
|---|---|
| CDN / WAF | 幾乎都有 |
| API Gateway | 幾乎都有，限流主力 |
| Service mesh sidecar | 有導入 mesh 才有 |
| 服務自己寫的限流器 | **少數情況才做** |
| 連線池 / 執行緒池上限 | 幾乎都有，但那是隱性背壓，不是限流器 |

成本差異也很明確：gateway 是 reactive，擋掉請求不佔 per-request 執行緒；
servlet 服務擋一個請求就佔一條 Tomcat 執行緒直到回應寫完。
被打的時候，服務層的限流器本身會變成瓶頸。

服務真的該自己做限流的時機，共通點是**「gateway 缺乏判斷所需的資訊」**：

- 多租戶配額（gateway 不知道這個 token 屬於哪個客戶、方案是什麼、用掉多少）
- 特別昂貴的端點（報表匯出、AI 呼叫）
- 沒有 service mesh，但內網服務間呼叫也會打爆你

## 取捨

- gateway 是單點：設定錯誤或故障時沒有第二道防線
- 內網呼叫繞過 gateway 時不受限流保護。Stage 8 加入服務間呼叫後要重新評估

## 後續

Stage 6 把限流上移到 gateway 之後，order-service 的 `FixedWindowRateLimitFilter`
（屆時已是 token bucket）保留為選用元件，預設 `ratelimit.orders.enabled=false`，
當成「需要時可以打開的最後防線」而不是常駐機制。
