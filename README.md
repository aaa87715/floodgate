# floodgate

[![CI](https://github.com/aaa87715/floodgate/actions/workflows/ci.yml/badge.svg)](https://github.com/aaa87715/floodgate/actions/workflows/ci.yml)

Spring Cloud 微服務棧的流量控制 —— 從單機記憶體限流一路做到 Redis 分散式限流，
每一層都先寫出問題、再用測試把問題釘下來，然後才換掉它。

技術棧：Java 21 · Spring Boot 4.0.8 · Spring Cloud 2025.1.3 · Postgres · Redis

演進路線：[LEARNING-PLAN.md](LEARNING-PLAN.md) ｜ 架構決策紀錄：[docs/adr](docs/adr)

---

## 整體架構

```
                        ┌──────────────────────┐
   client ──────────────▶   api-gateway :8080  │  ← 邊界限流（Redis / 分散式）
                        │  Spring Cloud Gateway│     RequestRateLimiter
                        │  (WebFlux, reactive) │     CircuitBreaker
                        └───────┬──────────────┘
                                │ lb://order-service
                                │ （服務名 → 實例位址）
                    ┌───────────┴───────────┐
                    ▼                       ▼
        ┌────────────────────┐   ┌────────────────────┐
        │ order-service :8081│   │ order-service :8091│  ← 第二個實例（Stage 4 才開）
        │ Web MVC (servlet)  │   │                    │
        │ 應用層限流 Bucket4j │   │                    │
        └─────┬────────┬─────┘   └────────────────────┘
              │        │
              ▼        ▼
        ┌─────────┐ ┌─────────┐
        │Postgres │ │  Redis  │  ← 限流計數器共用這顆
        │  :5432  │ │  :6379  │
        └─────────┘ └─────────┘

        ┌──────────────────────────┐
        │ discovery-server :8761   │  ← Eureka，所有服務向它註冊
        └──────────────────────────┘
```

限流在這個架構裡出現**兩層**，這是刻意的，兩層解的問題不同：

| 層 | 位置 | 狀態存哪 | 擋的是 |
|---|---|---|---|
| 邊界限流 | api-gateway | Redis（跨實例共用） | 惡意 / 失控的 client，在流量進入內網前 |
| 應用層限流 | order-service | 先記憶體，後 Redis | 保護單一服務的資源（DB 連線、執行緒池） |

---

## 模組

| 模組 | port | 職責 | pom 已備好的依賴 | 你要寫的 |
|---|---|---|---|---|
| `discovery-server` | 8761 | Eureka 服務註冊中心 | eureka-server, actuator | `@EnableEurekaServer` 啟動類別 |
| `api-gateway` | 8080 | 路由、邊界限流、熔斷 | gateway-server-webflux, eureka-client, **data-redis-reactive**, circuitbreaker-reactor-resilience4j | 啟動類別、`KeyResolver` bean、fallback controller |
| `order-service` | 8081 | 業務服務 | web, data-jpa, data-redis, validation, eureka-client, openfeign, circuitbreaker-resilience4j, **bucket4j_jdk17-core**, postgresql, h2(test), testcontainers(test) | 啟動類別、entity、repository、controller、限流 filter |

parent pom 統一給所有模組：`spring-boot-starter-actuator` + `spring-boot-starter-test`。

## 設定檔現況

三個 `application.yml` 已經寫好了，**裡面有部分答案**（尤其是 gateway 的路由與限流參數）。
建議：Stage 6 之前不要細看 gateway 那份；真的想從零來，就先把它清空。

| 檔案 | 已設定 |
|---|---|
| `discovery-server/.../application.yml` | port 8761、不向自己註冊、關自我保護 |
| `api-gateway/.../application.yml` | 兩條路由、RequestRateLimiter（replenishRate 5 / burst 10）、CircuitBreaker、Redis 連線、actuator gateway endpoint |
| `order-service/.../application.yml` | Postgres 連線、JPA、Redis、`ratelimit.orders.*` 參數、Resilience4j 實例設定 |
| `order-service/src/test/resources/application-test.yml` | H2、關 Eureka、限流上限調成 3（測試用） |

所有連線參數都吃環境變數，例如 `POSTGRES_HOST`、`REDIS_HOST`、`EUREKA_URI`，沒設就用 localhost 預設值。

## 版本

| 元件 | 版本 | 備註 |
|---|---|---|
| Java | 21 | |
| Spring Boot | 4.0.8 | |
| Spring Cloud | 2025.1.3 | 對應 spring-cloud-build 5.0.3，其 pom 指定 `spring-boot.version=4.0.8` |
| Bucket4j | 8.20.0 | groupId `com.bucket4j`，artifact `bucket4j_jdk17-core` |

版本改動請先確認 Spring Cloud 與 Boot 的對應關係，不是隨便挑最新就能配。

## 指令

```bash
mvn clean test-compile          # 驗證依賴與編譯（目前沒程式碼也會 BUILD SUCCESS）
mvn -pl order-service spring-boot:run
mvn -pl api-gateway spring-boot:run
mvn -pl discovery-server spring-boot:run
mvn clean test                  # 全部測試
mvn -pl order-service test -Dtest=RateLimitFilterTests   # 單一測試類別
```

多開一個 order-service 實例（Stage 4 用）：

```bash
mvn -pl order-service spring-boot:run -Dspring-boot.run.arguments=--server.port=8091
```

> 注意：在你寫出 `@SpringBootApplication` 主類別之前，`mvn package` 會失敗
> （spring-boot-maven-plugin 找不到 main class）。先用 `mvn compile` / `test-compile`。

## VS Code

安裝 `.vscode/extensions.json` 建議的套件後，F5 可以直接啟動各服務（`.vscode/launch.json` 已配好三個設定，
主類別名稱是預期你會用的，寫好就能直接跑）。
