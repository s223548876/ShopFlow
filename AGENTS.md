# ShopFlow 實作規範

本文件適用於整個 repository。所有實作者與自動化 agent 都必須遵守；若程式與 `docs/api-spec.md`、`docs/erd.md` 衝突，先修正設計一致性再實作。

## 範圍與簡化原則

- 第一版是單一 Vue SPA、單一 Spring Boot backend、單一 PostgreSQL。
- 不加入微服務、Kafka、Redis、Kubernetes、真實金流、guest cart 或逾時排程。
- 先搜尋現有能力，再使用 Java、Spring、Vue、瀏覽器或 PostgreSQL 原生功能；沒有明確需求不得新增 dependency 或抽象層。
- 禁止只有一個實作的 interface、通用 base controller/service/repository、推測性 factory 或事件匯流排。
- Testcontainers 是未來擴充點，不建立空白測試類別或未使用 dependency。

## 程式組織

### Backend

- 使用 Java 21、Spring Boot 3.5，按 `auth`、`catalog`、`cart`、`order`、`admin` 功能分包。
- `common` 只放 `api`、`error`、`openapi`、`security` 等跨模組能力；不要把功能邏輯放進萬用 common package。
- Controller：HTTP status、request/response DTO、Bean Validation。
- Service：商業規則、ownership check 與 `@Transactional` 邊界。
- Repository：JPA query 與 persistence，不包含 HTTP 或 DTO 邏輯。
- 使用明確 mapping method；未經確認不加入 MapStruct。

### Frontend

- 使用 Vue 3 Composition API、`<script setup lang="ts">` 與 TypeScript strict。
- 依 `auth`、`catalog`、`cart`、`orders`、`admin-products` feature 組織 view、component、Pinia store 與 API module。
- Axios instance 統一 `/api` base URL 與 Bearer token；token 存於 `sessionStorage`，登出時清除。
- Vite `/api`、`/actuator` proxy 只能存在於本機開發設定。Compose 完整啟動使用 Nginx 靜態檔服務與同路徑 reverse proxy。
- 不在 frontend 複製後端授權、價格或訂單狀態判定；前端檢查只用於使用體驗。

## API 與 DTO

- Entity 絕不可由 Controller 直接回傳或作為 request body。
- DTO 欄位與 endpoint 必須符合 `docs/api-spec.md`；OpenAPI 由 controller 與 DTO 產生並保持一致。
- 分頁一律回傳固定 PageResponse，不直接暴露 Spring `Page` serialization shape。
- 全域例外處理一律回傳 `timestamp`、`status`、`code`、`message`、`path`、`fieldErrors`。
- 使用 ISO 8601 UTC 時間；金額使用 `BigDecimal`，不得使用 `double` 或 `float`。
- Request 不得接受或信任 userId、role、Cart／OrderItem 商品價格、OrderItem subtotal 或 Order totalAmount；ADMIN 商品管理 DTO 可接受由後端驗證後保存的商品 price。

## Authentication 與授權

- 密碼只使用 BCrypt hash 儲存；不得記錄、回傳或保存明文密碼。
- 第一版只使用 30 分鐘 HS256 JWT Access Token，不實作 refresh token。
- JWT secret 必須由環境變數提供，長度與強度在啟動時驗證；禁止 fallback 到硬編碼 secret。
- 註冊永遠建立 `CUSTOMER`，request DTO 不得包含 role。
- 所有「我的」Cart、CartItem、Order 從 JWT principal 取得 userId。跨使用者資源回傳 404，避免洩漏存在性。
- `/api/admin/**` 僅允許 `ADMIN`；第一個管理員只能透過受控資料庫維運操作升級。
- 不可只靠前端隱藏按鈕保護管理功能。

## 商品與購物車規則

- 公開商品列表及 `GET /api/products/{productId}` 只查 active 商品；inactive 商品回傳 404。
- ADMIN 商品 endpoint 可查 active 與 inactive 商品。
- DELETE 商品只設定 `active = false`，不得物理刪除。
- CartItem 不保存價格；畫面 subtotal 與 estimatedTotal 只使用目前價格估算。
- 加入或修改 CartItem 時驗證 ownership、active、quantity 與當下庫存，但購物車不保留庫存。

## 建立訂單交易

`POST /api/orders` 必須由單一 Service transaction 完成，且只使用 JWT 使用者目前的完整購物車。

固定流程：

1. 讀取 JWT 使用者的 Cart 與 CartItem。
2. 依 productId 排序取得全部 Product 的悲觀寫入鎖。
3. 重新檢查 active、目前價格與庫存。
4. 使用 BigDecimal 在 backend 計算 unitPrice、subtotal、totalAmount。
5. 扣減庫存。
6. 建立 `PENDING_PAYMENT` Order。
7. 建立所有 OrderItem 快照。
8. 清空該使用者 CartItem。
9. 全部成功才 commit。

庫存扣減、Order、OrderItem 與購物車清空必須在同一交易。任一步失敗都必須拋出例外，使四者全部 rollback；不得 catch 後吞掉例外或提交部分結果。

## OrderItem 快照

每個 OrderItem 必須保存：

- `productId`
- `productName`
- `unitPrice`
- `quantity`
- `subtotal`

建立後不得因 Product 改名、改價、改分類或停用而改寫。所有 CUSTOMER 與 ADMIN 歷史訂單 response 都以 snapshot 為準；Product 僅用於追溯與取消回補庫存。

## 訂單狀態與取消

正常流程固定為：

`PENDING_PAYMENT → PAID → PROCESSING → SHIPPED → COMPLETED`

- `PENDING_PAYMENT → PAID` 只能由訂單本人呼叫模擬付款 endpoint。
- `PENDING_PAYMENT`、`PAID`、`PROCESSING` 可轉為 `CANCELLED`。
- `SHIPPED`、`COMPLETED` 不得取消或回補庫存。
- `COMPLETED`、`CANCELLED` 是 terminal state。

取消必須使用單一 transaction：

1. 悲觀鎖定 Order 並重新讀取目前狀態。
2. 已是 `CANCELLED` 時直接回傳，不回補。
3. `SHIPPED` 或 `COMPLETED` 時回傳 409，不回補。
4. 其他允許狀態才依 productId 排序鎖定 Product，按 OrderItem quantity 回補。
5. 回補與狀態更新同時 commit；失敗全部 rollback。

不得以 request flag、前端狀態或記憶體變數判斷是否已回補。Order row lock 與持久化的 `CANCELLED` 狀態是冪等門檻，重複或併發取消最多回補一次。

## Database 與 Flyway

- table、column、constraint 與 index 必須符合 `docs/erd.md`。
- migration 一旦套用不得修改；以新版本 migration 演進。
- JPA schema generation 不得在共享環境自動修改 schema。
- 金額使用 `NUMERIC(12,2)`；庫存與數量以 database CHECK constraint 保護。
- email 以小寫正規化，並由 `lower(email)` 唯一索引防止重複。

## 測試規則

- 使用 JUnit、Mockito、MockMvc；每個非平凡商業分支至少有一個會在規則破壞時失敗的測試。
- Service unit test 驗證商業結果與 repository interaction；不要為 getter、framework 或單純 mapping 製造測試。
- Controller/security test 使用 MockMvc 驗證 400、401、403、404、409 與 DTO schema。
- 修改商業規則時先建立失敗測試，再寫最小實作使其通過。

最低必要案例：

- 註冊無法指定 ADMIN，密碼交給 BCrypt。
- 竄改或傳入價格、總價、role、userId 不影響後端結果。
- CUSTOMER 無法存取 admin endpoint；使用者無法操作其他人的 cart/order。
- inactive 商品公開詳情 404，ADMIN 查詢 200。
- 下單重新讀取價格與庫存，成功後清空 cart。
- 下單任一步失敗時 Order、OrderItem、Product stock、CartItem 全部 rollback。
- 庫存不足與併發下單不超賣。
- 歷史訂單使用 OrderItem snapshot。
- 只有三個指定狀態可取消；SHIPPED、COMPLETED 不可取消。
- 重複或併發取消只回補一次，取消失敗全部 rollback。

H2 只驗證部分 JPA mapping 與基本 query，不得宣稱與 PostgreSQL 等價。PostgreSQL-specific migration、constraint、悲觀鎖與併發行為使用 Docker Compose 驗收；未來可用 Testcontainers 將其自動化。

## 敏感設定與 logging

- DB URL、username、password 與 JWT secret 由環境變數注入；目前同源 proxy 架構未啟用 CORS 設定。
- `.env`、token、密碼、Authorization header、DB credentials 不得提交或寫入 logs。
- repository 已提供只有 placeholder 的 `.env.example`，不得放入可用 secret。
- 錯誤 response 與 logs 不輸出 stack trace、SQL 參數中的敏感值或內部 class name。

## 變更完成條件

- 執行受影響的 unit、security、type check 與 build。
- 同步更新 OpenAPI 與相關 Markdown 規格。
- 確認沒有 Entity response、敏感值、未使用 dependency 或需求外基礎設施。
- 交叉檢查 entity、DTO、endpoint、狀態值、庫存、取消及授權規則一致。
