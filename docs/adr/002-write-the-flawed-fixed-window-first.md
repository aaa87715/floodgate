# ADR-002: 先實作有缺陷的 fixed window，再換成 token bucket

狀態：已採納

## 背景

這個專案的目的是理解限流，不是最快做出一個能用的限流器。
token bucket 是目前的主流選擇，三行 Bucket4j 設定就能完成。

## 決定

第一版刻意實作 fixed window，並且**寫一支測試把它的缺陷記錄下來**
（`FixedWindowRateLimitFilterTest#fixedWindowAllowedDoubleTheLimitAcossTheBoundary`），
確認缺陷存在之後才換成 token bucket。

## 理由

fixed window 的缺陷不是 bug，是演算法本質：它只知道「現在屬於哪一格」，
不知道「過去 60 秒發生了什麼」。所以在 window 邊界前後各打滿一次配額，
任意連續 60 秒內可以通過接近兩倍的量。

直接採用 token bucket 的話，「為什麼不用 fixed window」只會是一句背下來的答案。
先寫出來、再用測試證明它會漏，這個認知才站得住。

那支測試在換演算法之後也不會浪費 —— 它變成兩種演算法的對照基準。

## 取捨

- 多花一個階段的時間寫一個註定要被換掉的實作
- repo 歷史裡留著一個「錯誤」的版本。這是刻意的，用 git tag `stage-2` 標記

## 後續

換成 token bucket 時保留那支測試，改成驗證「同一個情境下通過量顯著低於兩倍」，
讓兩個版本的差異留在測試裡而不只是在記憶裡。
