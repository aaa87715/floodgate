# Architecture Decision Records

每一份 ADR 記錄一個「當時為什麼這樣決定」。重點不是結論，是**當下的處境和被放棄的選項** ——
半年後回來看，你會記得結論，但不會記得為什麼沒選另一條路。

## 格式

五段，每段幾句話就好，寫不滿表示這個決定還不值得寫成 ADR。

```markdown
# ADR-00X: 標題（用決定本身當標題，不要用問句）

狀態：提議中 | 已採納 | 已被 ADR-00Y 取代

## 背景
當時面對什麼問題、有什麼限制。

## 決定
一句話講結論。

## 理由
為什麼選這個。列出被放棄的選項和放棄的原因。

## 取捨
這個決定的代價是什麼。沒有代價的決定不用寫 ADR。

## 後續
什麼情況下應該重新考慮這個決定。
```

## 原則

- **一個決定一份**，不要寫成設計文件
- **不要改寫已採納的 ADR**。決定變了就寫一份新的，把舊的標成「已被 ADR-00Y 取代」。
  歷史本身就是資訊
- **只寫有取捨的決定**。「用 Java 21」不是決定，是前提

## 清單

| # | 決定 | 狀態 |
|---|---|---|
| [001](001-single-repo-multi-module.md) | 單一 repo、多模組 Maven | 已採納 |
| [002](002-write-the-flawed-fixed-window-first.md) | 先實作有缺陷的 fixed window | 已採納 |
| [003](003-inject-clock-for-testability.md) | 把時間抽成可注入的 Clock | 已採納 |
| [004](004-outbound-ports-depend-only-inward.md) | 出站 port 只能依賴原生型別與 domain | 已採納 |
| [005](005-where-rate-limiting-lives.md) | 限流放在哪一層 | 已採納 |
| [006](006-rate-limit-algorithm-behind-an-interface.md) | 限流演算法抽成介面，由設定選擇實作 | 已採納 |
| [007](007-rate-limit-state-must-live-outside-the-instance.md) | 限流狀態必須放在實例之外（Redis） | 已採納 |
| [008](008-fail-open-when-redis-is-unavailable.md) | Redis 不可用時 fail-open | 已採納 |
