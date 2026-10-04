# ADR-003: 把時間抽成可注入的 Clock

狀態：已採納

## 背景

限流的邏輯有一半是時間判斷：這個 window 過期了沒。
原本直接呼叫 `System.currentTimeMillis()`，導致那一半邏輯無法測試 ——
要驗證「跨越 window 邊界」只剩下 `Thread.sleep()` 一條路。

## 決定

宣告一個 `Clock` bean（`infra/TimeConfig`），需要時間的元件注入它，
用 `clock.millis()` 取代 `System.currentTimeMillis()`。

## 理由

`System.currentTimeMillis()` 是靜態方法，無法在測試中替換。
這跟資料庫、外部服務是同一類問題：**時間是一個不可控的外部依賴**。
這個專案已經把資料庫（`SaveOrderPort`）和商品價格（`LoadProductPort`）
都變成可注入的介面，時間是唯一漏掉的那一個。

替換後，測試可以用 `@MockitoBean Clock` 把時間瞬間撥快 61 秒，
整支測試執行時間接近零，而且 window 參數維持正式環境的 `1m`，
測的就是真實設定而不是為了測試縮短過的版本。

替代方案與放棄原因：

- `Thread.sleep()`：測試變慢，而且時間抓不準會產生 flaky test
- 把 window 縮短成 1 秒：還是要 sleep，而且測的不是真實參數
- Mockito `mockStatic`：做得到，但需要位元碼改寫、效能差、影響整個測試類別

## 取捨

- production 程式碼多了一個建構子參數和一個 config 類別
- `@MockitoBean Clock` 會替換掉整個 context 的時鐘，其他元件也會看到假時間

## 後續

只在「時間參與業務判斷」的地方注入 Clock。純粹記錄用途的時間戳
（例如 `Order.createdAt`）繼續用 `Instant.now()`，不需要為了形式統一而注入。
