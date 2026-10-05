# 限流 × 微服務 學習路線

規則：**每一個 Stage 都要能實際跑起來並通過驗收，才准進下一個**。
不要一次把三個服務都寫完再一起 debug，那會變成除錯地獄而不是學習。

進度追蹤（做完打勾）：

- [ ] Stage 0　環境與骨架
- [x] Stage 1　order-service 單體跑起來　（20 支測試全綠）
- [x] Stage 2　手寫 fixed window 限流（故意寫出有缺陷的版本）
- [x] Stage 3　換成 token bucket（Bucket4j）
- [x] Stage 4　開第二個實例 → 親眼看到限流失效　（配額 20，實測通過 40）
- [ ] Stage 5　Redis + Lua 分散式限流
- [ ] Stage 6　限流上移到 api-gateway
- [ ] Stage 7　Eureka 服務發現
- [ ] Stage 8　服務間呼叫 + 熔斷
- [ ] Stage 9　觀測與壓測

每個 Stage 的固定流程：**先想清楚要驗證什麼 → 寫測試 → 實作 → 手動驗收 → 把觀察記下來**。

---

## Stage 0　環境與骨架　`約 30 分鐘`

### 目標
確認 build 通、Redis 與 Postgres 起得來。這階段不寫 Java。

### 要做的
在專案根目錄建 `docker-compose.yml`：

```yaml
services:
  postgres:
    image: postgres:17-alpine
    environment:
      POSTGRES_DB: ratelimit
      POSTGRES_USER: ratelimit
      POSTGRES_PASSWORD: ratelimit
    ports: ["5433:5432"]   # 本機已裝 Postgres 的話 5432 會被佔走
  redis:
    image: redis:7-alpine
    ports: ["6379:6379"]
```

### 驗收
```bash
mvn clean test-compile        # BUILD SUCCESS
docker compose up -d
docker compose exec redis redis-cli ping     # PONG
```

### 坑
Windows 要先開 Docker Desktop。埠被佔用的話改 compose 冒號左邊那個數字，右邊別動。

---

## Stage 1　order-service 單體跑起來　`約 1-2 小時`

### 目標
先有一個能用的服務，這階段**完全不碰限流**。

### 要寫的
```
order-service/src/main/java/com/willie/ratelimit/order/
├── OrderServiceApplication.java      @SpringBootApplication
├── domain/Order.java                 @Entity，表名用 orders
├── domain/OrderRepository.java       extends JpaRepository<Order, Long>
└── web/OrderController.java          GET /api/orders、GET /{id}、POST /api/orders
```

先把 `eureka.client.enabled: false` 加進 application.yml，Stage 7 再打開，
不然啟動會一直噴連不到 Eureka 的錯誤日誌。

### 驗收
```bash
mvn -pl order-service spring-boot:run

curl -X POST localhost:8081/api/orders -H "Content-Type: application/json" -d "{\"item\":\"keyboard\",\"quantity\":2}"
# 期望 201 + 回傳含 id
curl localhost:8081/api/orders          # 期望陣列裡有剛剛那筆
curl localhost:8081/actuator/health     # status UP
```
再加一個 `@SpringBootTest` + MockMvc 測試，跑 `mvn -pl order-service test` 綠燈。

### 概念
- 三層切分（controller / repository / entity）與 DTO：別把 entity 直接回給前端
- `spring.jpa.hibernate.ddl-auto` 各個值的差別，正式環境為什麼不能用 `update`

### 坑
- `order` 是 SQL 保留字 → `@Table(name = "orders")`
- Boot 4 改了部分測試 annotation 的 package，**讓 IDE 自動 import，不要抄 Boot 3 的範例**
- `@AutoConfigureMockMvc` / `@WebMvcTest` 已不在 `spring-boot-starter-test` 裡，要另外加
  `spring-boot-starter-webmvc-test`（package 變成 `org.springframework.boot.webmvc.test.autoconfigure`）
- `@MockBean` 已從 Boot 4 移除，改用 `@MockitoBean`
  （`org.springframework.test.context.bean.override.mockito.MockitoBean`）
- JPA entity 需要一個 protected 無參數建構子

---

## Stage 2　手寫 fixed window 限流　`約 2 小時`

### 目標
**故意先寫一個有缺陷的版本**，親手把它的缺陷測出來。這步不能跳，跳了就不懂後面為什麼要換。

### 要寫的
`ratelimit/FixedWindowRateLimitFilter.java`，繼承 `OncePerRequestFilter`：
- key = client IP（`X-Forwarded-For` 第一段，沒有就 `request.getRemoteAddr()`）
- `ConcurrentHashMap<String, AtomicInteger>`，每 60 秒整點重置
- 超過上限回 `429`，帶 `Retry-After` 與 `X-RateLimit-Remaining` header

### 驗收
先驗基本行為（PowerShell）：
```powershell
1..15 | ForEach-Object {
  (Invoke-WebRequest -Uri http://localhost:8081/api/orders -SkipHttpErrorCheck).StatusCode
}
# 期望：前 N 個 200，之後 429
```
再驗**它的缺陷**，這才是重點：
在 window 快結束時連打 N 次，跨過邊界後立刻再打 N 次，
計算「任意連續 60 秒」內實際通過的請求數，你會看到**接近上限的 2 倍**。

### 概念
fixed window 的邊界突刺（burst at boundary）為什麼無法避免。

### 坑
- `ConcurrentHashMap` 的 key 會無限成長 → 記憶體洩漏，想想怎麼清
- filter 執行順序：限流要放在身分驗證之前還是之後？兩種選擇的攻防差異
- `@Component` 的 filter 會套用到所有路徑，記得用 `shouldNotFilter` 排除 actuator

---

## Stage 3　換成 token bucket（Bucket4j）　`約 1.5 小時`

### 目標
用成熟的演算法取代手寫版，並比較兩者行為差異。

### 要寫的
- `ratelimit/RateLimitProperties.java`（`@ConfigurationProperties(prefix = "ratelimit.orders")`，yml 裡參數已經定義好了）
- `ratelimit/Bucket4jRateLimitFilter.java`

Bucket4j 8.20 的 API 長這樣（舊教學的 `Bandwidth.classic()` / `simple()` 已 deprecated）：
```java
Bandwidth limit = Bandwidth.builder()
        .capacity(capacity)
        .refillGreedy(refillTokens, refillPeriod)
        .build();
Bucket bucket = Bucket.builder().addLimit(limit).build();
ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
```

### 驗收
- 瞬間連打 `capacity` 次 → 全部 200（token bucket 允許 burst，fixed window 不會這樣）
- 之後的通過速率穩定收斂到 `refillTokens / refillPeriod`
- `X-RateLimit-Remaining` header 遞減正確
- 測試：`application-test.yml` 已把 capacity 設成 3，寫「第 4 次應為 429」與「不同 IP 各自一桶」兩個測試

### 概念
- token bucket vs leaky bucket：前者容忍 burst，後者輸出恆定
- `refillGreedy` 與 `refillIntervally` 的差別（平滑補充 vs 整批補充）

### 坑
groupId 是 `com.bucket4j`，Java 17+ 用 `bucket4j_jdk17-core`。
舊的 `bucket4j-core` 只出到 8.10.1，別抓錯。

---

## Stage 4　開第二個實例，看見問題　`約 30 分鐘`　★ 轉折點

### 目標
不寫任何新程式，只是把服務多開一份，讓你**親眼看到記憶體限流在水平擴展下直接失效**。
這是整條路線最重要的一步。

### 要做的
```powershell
# 終端 A
mvn -pl order-service spring-boot:run

# 終端 B（PowerShell 會把 -Dspring-boot.run.arguments=... 從第一個點切開，
#          所以改用環境變數比較省事）
$env:SERVER_PORT = 8091
mvn -pl order-service spring-boot:run
```

```powershell
# 需要 PowerShell 7+（-SkipHttpErrorCheck 在 5.1 不存在）
$ok = 0; $blocked = 0
1..60 | ForEach-Object {
    $port = if ($_ % 2 -eq 0) { 8081 } else { 8091 }
    $r = Invoke-WebRequest -Uri "http://localhost:$port/api/orders" `
         -Headers @{ "X-Forwarded-For" = "203.0.113.99" } -SkipHttpErrorCheck
    if ($r.StatusCode -eq 200) { $ok++ } else { $blocked++ }
}
"通過 $ok 次，擋掉 $blocked 次"
```

### 實測結果　★ Stage 5 的對照基準

設定 `capacity = 20`，60 個請求輪流打兩個實例：

```
通過 40 次，擋掉 20 次
```

每個實例各收到 30 個請求，各自放行自己那 20 個額度。
**限流上限 = 設定值 × 實例數**，而且方向最糟 —— 實例越多限制越鬆，
k8s 高負載自動擴容時，保護反而最弱。

Stage 5 接上 Redis 之後，同樣的指令要回到 **通過 20 次**。

### 思考題（寫下你的答案，之後驗證）
- 如果有 10 個實例呢？你設定的「每分鐘 20 次」實際變成多少？
- 有沒有辦法不靠外部儲存就解決？（提示：gossip、一致性雜湊，各自的代價是什麼）

---

## Stage 5　Redis + Lua 分散式限流　`約 3-4 小時`

### 目標
**自己從零寫一次**，寫完再去讀 Spring 的 `RedisRateLimiter` 原始碼對答案。
（它就在 `spring-cloud-gateway-server-webflux` 的 jar 裡，附帶的 Lua 腳本也在 jar 內。）

### 要寫的
- `resources/scripts/token_bucket.lua` — 在 Redis 裡原子地完成「讀取 → 計算補充 → 扣減 → 寫回」
- `ratelimit/RedisRateLimitFilter.java` — 用 `StringRedisTemplate` + `DefaultRedisScript` 執行

### 驗收
- 兩個實例同時跑，**總通過量回到單一上限**（跟 Stage 4 記下的數字對照）
- `redis-cli MONITOR` 看得到 key 的讀寫，`TTL` 有正確設定
- 殺掉 Redis，觀察你的服務怎麼反應 → 這就是 fail-open / fail-closed 的抉擇

### 概念
- 為什麼「先 GET 再 SET」在併發下是錯的（把那個 race condition 的時序畫出來）
- Redis 單執行緒 + Lua 腳本如何提供原子性
- 時間基準要用 **Redis 的 `TIME`** 而不是各個 JVM 的 `System.currentTimeMillis()`（多台機器時鐘不同步）
- key 設計與 TTL：key 不過期會累積成記憶體問題

### 坑
`RedisTemplate` 預設的 JDK 序列化會讓 key 在 redis-cli 裡看起來是亂碼，用 `StringRedisTemplate`。

---

## Stage 6　限流上移到 api-gateway　`約 2 小時`

### 目標
讓流量在進入內網前就被擋掉，並理解為什麼**兩層限流都要**。

### 要寫的
```
api-gateway/src/main/java/com/willie/ratelimit/gateway/
├── ApiGatewayApplication.java
├── RateLimiterConfig.java     KeyResolver bean（IP / API key / route 三種切法）
└── FallbackController.java    CircuitBreaker 的 fallbackUri 落點
```
路由設定 `application.yml` 已經寫好了，這階段的功課是**看懂每一個參數在做什麼**，
特別是 `key-resolver: "#{@clientIpKeyResolver}"` 這個 SpEL bean 參照。

### 驗收
- 打 `localhost:8080/api/orders`，超過 burstCapacity 後回 429
- 直連 `localhost:8081/api/orders` 仍然依照 order-service 自己的上限運作
- 切換 KeyResolver 後，用不同 `X-API-Key` 打，桶子確實分開

### 概念
- 邊界限流 vs 服務內限流的取捨。**正式環境通常只留 gateway 這一層**，
  服務自保改用連線池上限 + Resilience4j 的 Bulkhead / CircuitBreaker。
  服務真的自己做限流的時機是「gateway 缺乏資訊」：多租戶配額、特別昂貴的端點、
  沒有 service mesh 又擔心內網呼叫。Stage 2-5 在服務裡寫限流一半是教具。
- gateway 是 reactive，擋掉請求不佔 per-request 執行緒；服務是 servlet，
  擋一個請求就佔一條 Tomcat 執行緒直到回應寫完 —— 這是「邊界比較省」的具體原因。
- KeyResolver 選 IP 的風險：NAT 後面整棟樓共用一個 IP；`X-Forwarded-For` 可以偽造，你要信到第幾層？

### 坑
- 設定前綴是 `spring.cloud.gateway.server.webflux.*`（2025.x 改過，舊教學都是錯的）
- artifact 是 `spring-cloud-starter-gateway-server-webflux`，舊的 `spring-cloud-starter-gateway` 停在 4.3.5
- gateway 是 WebFlux，**不要**加 `spring-boot-starter-web`
- RedisRateLimiter 需要 reactive redis（pom 已經備好）

---

## Stage 7　Eureka 服務發現　`約 1 小時`

### 目標
路由從寫死的 host:port 換成服務名稱。

### 要寫的
`discovery-server/.../DiscoveryServerApplication.java`，加 `@EnableEurekaServer`。
然後把 order-service 和 api-gateway 的 `eureka.client.enabled` 打開。

### 驗收
- 開 http://localhost:8761 ，看到 `ORDER-SERVICE` 與 `API-GATEWAY` 都註冊上來
- gateway 用 `lb://order-service` 路由仍然通
- 開第二個 order-service 實例 → Eureka 上出現兩個實例，gateway 自動輪詢
- **關掉 order-service → gateway 回 503**，觀察要多久它才從清單消失

### 概念
- client-side 負載均衡（Spring Cloud LoadBalancer）與 server-side 的差別
- 心跳、租約到期、自我保護模式（yml 裡已經關掉，想想正式環境為什麼要開）

---

## Stage 8　服務間呼叫 + 熔斷　`約 2-3 小時`

### 目標
搞清楚限流、熔斷、重試、逾時各自解什麼問題。

### 要寫的
- 新增第四個模組 `inventory-service`（複製 order-service 的 pom 結構改個名，port 8082）
- order-service 加 `@FeignClient(name = "inventory-service")` 的介面
- 用 `CircuitBreakerFactory` 包住 Feign 呼叫，提供 fallback

### 驗收
- 正常時 order 查得到庫存
- **關掉 inventory-service** → 回 fallback 而不是 500，且回應時間沒有暴增
- `curl localhost:8081/actuator/circuitbreakers` 看到狀態從 CLOSED → OPEN → HALF_OPEN

### 概念
- 限流（保護自己不被打爆）vs 熔斷（保護自己不被下游拖死）vs 艙壁隔離
- **重試會放大流量**：下游已經在限流了，你還重試三次會怎樣？
- 逾時設定必須小於上游的逾時，否則整條鏈一起卡死

---

## Stage 9　觀測與壓測　`約 2 小時`

### 目標
用數據驗證你的限流真的是你以為的形狀。

### 要做的
- actuator 暴露 metrics，自訂一個 counter 記錄 429 次數
- 用 k6 或 hey 壓測，逐步拉高 RPS

### 驗收
畫出「送出 RPS」對「通過 RPS」的曲線。通過量應該在 `replenishRate` 處**打平成一條水平線**，
轉折點就是你設定的限流值。對不上就是哪裡寫錯了。

### 概念
- 限流參數怎麼定：從下游容量（DB 連線數、執行緒池大小）反推，不是拍腦袋
- 被限流的請求對 p99 延遲的影響（429 應該要**很快**回，不能排隊）

---

## 延伸題（路線走完後自己挑）

- sliding window counter：精準度與記憶體的折衷，比 fixed window 好在哪
- 每租戶動態配額：配額存 Redis，改設定不用重啟
- 限流器本身故障：fail-open 還是 fail-closed？你的服務該選哪個，為什麼
- 改用 gateway 內建的 `Bucket4jRateLimiter`，跟 `RedisRateLimiter` 比較
- Resilience4j 的 `RateLimiter` 與 Bucket4j 的差異（前者偏並發控制，後者偏配額）
- Redis 掛了怎麼辦：本地降級桶 + 熔斷
- 讀 `RedisRateLimiter` 的 Lua 腳本，跟你 Stage 5 自己寫的比較

---

## 參考

- Spring Cloud Gateway 的限流實作：解開 `spring-cloud-gateway-server-webflux-5.0.3.jar`，
  看 `org/springframework/cloud/gateway/filter/ratelimit/` 底下的類別與內附的 Lua 腳本
- 設定屬性有哪些：讀 jar 裡的 `META-INF/spring-configuration-metadata.json`，
  比 google 出來的部落格可靠，版本一定對得上
