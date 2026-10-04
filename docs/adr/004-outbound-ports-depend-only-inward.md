# ADR-004: 出站 port 的簽章只能出現原生型別與 domain 型別

狀態：已採納

## 背景

第一版的 `LoadOrderPort` 寫成 `Optional<Order> findById(OrderQuery query)`，
其中 `OrderQuery` 是入站的 command 物件。結果 `OrderPersistenceAdapter`
（純粹負責跟資料庫溝通的類別）被迫 import 了 `application.port.in.command.OrderQuery`。

## 決定

出站 port 的方法簽章只能出現三種型別：原生型別、domain 型別、
或 out 層自己定義的型別（例如查詢專用的讀取 DTO）。

改成 `Optional<Order> findById(Long orderId)`，拆解 command 是 use case 的工作。

## 理由

六角形的左側（誰驅動我）和右側（我驅動誰）應該彼此不認識，只透過中間的 core 連接。
出站 port 引用入站型別，等於在兩個 adapter 之間開了一條繞過核心的捷徑。

實際代價：

- 其他呼叫路徑（排程、message consumer、use case 內部）手上只有 id，
  要為了滿足簽章硬造一個 `new OrderQuery(id)`
- `OrderQuery` 會跟著 API 需求長大（分頁、過濾），連帶讓 `findById` 的語意歪掉
- 測試 persistence adapter 要 import 不相干的入站型別

判斷法：**把左側整個換掉（HTTP 改成 gRPC），右側要不要跟著改？**
`Order` 不會因為換了輸入管道而消失，`OrderQuery` 會。

注意這裡**不是**「port 不可以依賴任何東西」。`SaveOrderPort.save(Order)`
依賴 domain 型別是正確的 —— 往內依賴永遠可以，往外和橫向不行。

## 取捨

- use case 多一行拆解 command 的程式碼
- 參數多的查詢會讓 port 的簽章變長。真的變長時，改成在 out 層定義專用的查詢物件，
  而不是回頭借用入站的 command

## 後續

Stage 8 加入服務間呼叫時，`InventoryClient` 這類出站 port 同樣適用這條規則。
