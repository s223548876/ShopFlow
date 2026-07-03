# ShopFlow

ShopFlow 是一個適合作品集展示的前後端分離電商系統。第一版聚焦完整但可控的購物流程：使用者驗證、商品瀏覽、購物車、後端可信任的結帳計算、模擬付款，以及管理員商品與訂單管理。

目前已完成 [開發路線圖](docs/development-roadmap.md) 第 1 階段：Java 21 / Spring Boot 3.5 backend 骨架，以及 Vue 3 / TypeScript / Vite frontend 骨架。尚未加入資料庫、Docker、API 或商業功能。

## 技術架構

| 區域 | 技術 |
| --- | --- |
| Frontend | Vue 3、TypeScript、Vite、Vue Router、Pinia、Axios、Element Plus |
| Backend | Java 21、Spring Boot 3.5、Spring Security、Spring Data JPA |
| Database | PostgreSQL、Flyway |
| Authentication | JWT Access Token、BCrypt |
| API documentation | OpenAPI / Swagger UI |
| Local runtime | Docker Compose、Nginx |
| Backend testing | JUnit、Mockito、MockMvc |

後端採單一 Spring Boot 應用程式，依 `auth`、`catalog`、`cart`、`order`、`admin` 功能分組；不是微服務。資料庫是唯一持久化來源。

### 開發與完整容器啟動

- 直接在本機開發前端時，Vite development server 才會將 `/api` proxy 至本機 backend。
- Docker Compose 完整啟動時，Vue 會先建置為靜態檔，由 Nginx 提供；Nginx 同時把 `/api` 反向代理至 backend service。
- 瀏覽器不直接使用 Docker 內部 service name，也不在正式靜態站台依賴 Vite proxy。

詳細資料流見 [架構說明](docs/architecture.md)。

## 本機啟動

需求：Java 21、Maven 3.9、Node.js `^20.19.0` 或 `>=22.12.0`，以及 npm。

### Backend

```bash
cd backend
mvn test
mvn spring-boot:run
```

目前 backend 只驗證 Spring Boot application context，尚未加入 Web/API dependency，因此不會監聽 HTTP port。

### Frontend

```bash
cd frontend
npm ci
npm run dev
```

Vite development server 預設顯示於 `http://localhost:5173`。品質檢查：

```bash
npm run lint
npm run type-check
npm run build
```

第 1 階段尚未設定 Vite `/api` proxy；它屬於開發路線圖第 2 階段。

## 第一版功能

### 使用者

- 註冊、登入及 JWT 驗證
- 商品列表、分類篩選、關鍵字搜尋、分頁及商品詳情
- 新增、修改及刪除自己的購物車品項
- 由購物車建立訂單並模擬付款
- 查看自己的訂單列表與詳情

### 管理員

- 商品建立、查詢、更新及停用
- 查看 active 與 inactive 商品
- 調整商品庫存
- 查看全部訂單並依合法狀態轉移更新訂單

### 訂單狀態

主要流程為：

`PENDING_PAYMENT → PAID → PROCESSING → SHIPPED → COMPLETED`

只有 `PENDING_PAYMENT`、`PAID`、`PROCESSING` 可轉為 `CANCELLED`。`SHIPPED`、`COMPLETED` 與已取消訂單不可再次取消或回補庫存。

## 安全與商業原則

- 後端不接受前端指定的角色、userId、商品單價或訂單總價。
- 「我的」購物車與訂單一律由 JWT principal 取得使用者身分。
- 下單時後端重新查詢並鎖定商品，檢查 active 狀態、庫存與目前價格，再計算總額。
- 扣減庫存、建立 Order、建立 OrderItem 及清空購物車必須位於同一交易；任一步失敗全部 rollback。
- OrderItem 保存商品 ID、名稱、單價、數量與小計快照；歷史訂單不得用目前商品資料覆蓋快照。
- 商品刪除採停用。公開商品 API 對 inactive 商品回傳 404，管理員端仍可查詢。
- 取消訂單須鎖定訂單並檢查目前狀態，庫存只能回補一次。
- 密碼使用 BCrypt 雜湊，Entity 不直接回傳，所有 API 使用 DTO。
- 驗證失敗與業務錯誤使用統一錯誤格式。
- JWT secret、資料庫密碼及其他敏感設定只由環境變數提供，不提交至 Git。

## 文件

- [架構說明](docs/architecture.md)：前端、Nginx、後端、資料庫及交易資料流。
- [ERD](docs/erd.md)：資料表、欄位、關聯與約束。
- [API 規格](docs/api-spec.md)：endpoint、權限、DTO、範例與錯誤。
- [開發路線圖](docs/development-roadmap.md)：可逐步驗收的實作順序。
- [AGENTS.md](AGENTS.md)：實作者必須遵守的程式、測試與安全規則。

## 未來規劃

- 使用 Testcontainers 驗證 PostgreSQL、Flyway 與悲觀鎖行為
- 加入前端單元與元件測試
- 評估 refresh token、撤銷及金鑰輪替策略
- 商品圖片儲存與管理
- 分類管理及階層分類
- 商品量成長後評估 PostgreSQL 全文搜尋或 trigram index
- 庫存異動稽核紀錄與待付款訂單逾時取消

## 第一版不包含

微服務、Kafka、Redis、Kubernetes、真實金流、guest cart、自動逾時取消及可由公開 API 指定角色的功能均不在第一版範圍。
