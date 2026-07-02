# ShopFlow 開發路線圖

## 原則

- 每一步只交付一個可驗收能力，先完成最小正確版本再前進。
- 優先使用 Spring、Vue、PostgreSQL 與已指定套件的原生能力，不建立推測性抽象。
- 商業規則放在 Service，授權身分來自 JWT principal，Entity 不直接成為 API response。
- 每一步完成後執行當前全部測試；需要 migration 的變更只新增 Flyway migration，不修改已套用版本。

## 1. Repository 與品質基線

建立 `frontend/`、`backend/` 與既有 `docs/` 結構，固定 Java 21、Spring Boot 3.5、Vue 3、TypeScript strict 與 Vite。

完成條件：

- backend 可啟動並執行空測試集。
- frontend 可完成 TypeScript check 與 production build。
- 不加入需求外 dependency、base service、factory 或微服務骨架。

## 2. 本機與 Compose 執行環境

先建立 PostgreSQL，再完成兩條互斥的前端執行路徑：

1. 直接開發：Vite development server 的 `/api` proxy 指向本機 backend。
2. Compose 完整啟動：建置 Vue 靜態檔，由 Nginx 提供並將 `/api` 反向代理至 backend service。

完成條件：

- 完整 Compose 可啟動 frontend、backend、postgres 三個 service。
- SPA history fallback 正常，瀏覽器可透過 Nginx 同源 `/api` 存取 backend。
- Vite proxy 不出現在 production runtime。
- DB credentials、JWT secret 等敏感值由環境變數注入。

## 3. Flyway schema 與 persistence model

依 [ERD](erd.md) 建立 roles、users、categories、products、carts、cart_items、orders、order_items，並加入 FK、UNIQUE、CHECK 與必要索引。以 migration 建立 `CUSTOMER`、`ADMIN` 角色及初始分類。

完成條件：

- 空 PostgreSQL 啟動時 migration 可完整套用。
- email 大小寫不可重複；每位 User 最多一個 Cart；同商品在同一 Cart 最多一筆。
- 商品價格大於 0、庫存非負、CartItem 與 OrderItem quantity 大於 0。
- Order status 只接受六個固定值。

## 4. 共通 API、錯誤與 OpenAPI 基線

建立明確 DTO、Bean Validation、全域例外處理、PageResponse 及 OpenAPI security scheme。

完成條件：

- validation、authentication、authorization、not found 與 conflict 使用 [API 規格](api-spec.md) 的統一錯誤格式。
- Swagger UI 能顯示 Bearer authentication 與 response schema。
- Entity 不會被 controller 直接回傳。

## 5. 註冊、登入與 JWT

實作 CUSTOMER 註冊、BCrypt password hash、登入與 30 分鐘 HS256 Access Token。Security filter 從 JWT 建立 principal；不提供 refresh token。

完成條件：

- 註冊 request 無法指定 role 或 userId，建立結果永遠是 CUSTOMER。
- 密碼只以 BCrypt hash 儲存，錯誤登入不透露 email 是否存在。
- 缺少、無效、過期 JWT 回傳 401；CUSTOMER 存取 admin endpoint 回傳 403。
- JUnit/Mockito 驗證註冊與登入；MockMvc 驗證 route policy。

## 6. 公開分類與商品目錄

實作分類列表、active 商品列表、關鍵字搜尋、分類篩選、排序、分頁與商品詳情。

完成條件：

- 公開列表與詳情都只查 active 商品。
- inactive 商品詳情與不存在商品一律回傳 `404 PRODUCT_NOT_FOUND`。
- size 上限 100，只允許規格中的 sort 欄位。
- 測試搜尋條件、分頁驗證及 inactive 404。

## 7. 管理員商品與庫存

實作 ADMIN 商品列表、詳情、建立、完整更新、停用、重新啟用及絕對庫存設定。

完成條件：

- ADMIN 可以查到 inactive 商品；公開 endpoint 仍回傳 404。
- DELETE 只把 active 設為 false，重複停用具冪等性。
- 庫存不能為負；產品基本資料更新不偷偷修改庫存。
- CUSTOMER 對所有 `/api/admin/**` 操作都得到 403。

## 8. 使用者購物車

建立每位使用者唯一 Cart，實作讀取、新增、修改數量及刪除 CartItem。所有 ownership check 使用 JWT principal。

完成條件：

- Request 不接受 userId；其他使用者的 itemId 回傳 404。
- 商品加入購物車時必須 active，quantity 介於 1–999 且當下不超過庫存。
- Cart response 的目前價格及 estimatedTotal 清楚標為非結帳依據。
- 商品停用後，既有 CartItem 可顯示為 unavailable，但不能下單。

## 9. 原子化建立訂單

實作由整個購物車建立訂單的單一 Service transaction。

交易順序：

1. 由 JWT principal 讀取使用者與購物車。
2. 依 productId 排序，悲觀鎖定全部 Product。
3. 重查 active、目前價格與庫存，由 backend 使用 BigDecimal 計算。
4. 扣減庫存。
5. 建立 `PENDING_PAYMENT` Order。
6. 建立包含 `productId`、`productName`、`unitPrice`、`quantity`、`subtotal` 的 OrderItem 快照。
7. 清空該使用者所有 CartItem。
8. commit 後回傳 OrderResponse。

完成條件：

- Request 不接受商品清單、價格、總價或 userId。
- 庫存不足、商品停用或任何 persistence 失敗時，庫存、Order、OrderItem 與 CartItem 全部 rollback。
- 成功時購物車為空，庫存只扣一次，totalAmount 等於所有 snapshot subtotal 總和。
- Mockito 測試重新讀價、操作順序與失敗 rollback 契約；真實 PostgreSQL 鎖定測試保留給未來 Testcontainers。

## 10. 自己的訂單與模擬付款

實作 CUSTOMER 訂單列表、詳情及 `PENDING_PAYMENT → PAID` 模擬付款。

完成條件：

- 只從 JWT principal 限制訂單 owner；跨使用者查詢或付款回傳 404。
- 模擬付款固定成功並設定 paidAt，不建立 Payment entity、不接收卡號。
- 非 PENDING_PAYMENT 訂單不可付款。
- 歷史 response 使用 OrderItem snapshot，不因 Product 改名、改價或停用而改變。

## 11. 管理員訂單與取消

實作 ADMIN 全部訂單查詢、詳情及狀態轉移。

完成條件：

- 唯一正常流程為 `PENDING_PAYMENT → PAID → PROCESSING → SHIPPED → COMPLETED`，其中 PAID 只由付款 endpoint 產生。
- 只有 `PENDING_PAYMENT`、`PAID`、`PROCESSING` 可轉為 `CANCELLED`。
- `SHIPPED`、`COMPLETED` 不可取消，回傳 409 且不改庫存。
- 取消交易先鎖 Order，再依 productId 排序鎖 Product、回補庫存並更新狀態。
- 重複或併發取消最多回補一次；任何回補失敗時庫存和狀態全部 rollback。
- JUnit/Mockito 覆蓋所有合法與非法狀態；未來 Testcontainers 驗證真正併發取消。

## 12. 安全與契約驗收

交叉驗證 OpenAPI、Markdown 規格及實際 response。

必要情境：

- 修改或額外傳入價格、總價、role、userId 不會影響後端結果。
- CUSTOMER 不能管理商品、庫存或全部訂單。
- 使用者不能讀取或修改其他人的 Cart、CartItem、Order。
- inactive 商品公開詳情為 404，但 ADMIN 可查詢。
- 建立訂單任一步失敗全部 rollback。
- 合法取消只回補一次，SHIPPED 與 COMPLETED 永不回補。
- 敏感設定未被提交，logs 不輸出密碼、JWT 或 DB credentials。

## Testcontainers 擴充點

第一版不建立空白 Testcontainers 類別、抽象 base test 或未使用 dependency。需要驗證下列真實 PostgreSQL 行為時再加入：

- Flyway 從空資料庫套用及 constraint 行為
- `ILIKE` 與 repository query
- 多訂單悲觀鎖與防止超賣
- 取消競爭時庫存只回補一次
- transaction rollback 對 Order、OrderItem、Product、CartItem 的完整影響
