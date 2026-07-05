# ShopFlow

ShopFlow 是前後端分離的電商作品集專案，使用單一 Git repository 管理 Vue frontend、Spring Boot backend 與文件。Backend 採功能模組化單體（modular monolith），不是微服務架構。

## 技術棧

| 區域 | 技術 |
| --- | --- |
| Frontend | Vue 3、TypeScript strict、Vite、Vue Router、Pinia、Axios、Element Plus、Vitest |
| Backend | Java 21、Spring Boot 3.5、Maven、Spring Security、Spring Data JPA、JUnit、Mockito、MockMvc |
| Database | PostgreSQL、Flyway；test scope H2 |
| API | REST、OpenAPI 3、Swagger UI、JWT Access Token |
| Local runtime | Docker Compose、Nginx reverse proxy |

Docker Compose 完整啟動時，Nginx 提供 Vue 靜態檔並將 `/api`、`/actuator` 反向代理至單一 Spring Boot backend。Backend 依 `auth`、`catalog`、`cart`、`order`、`admin`、`common` 組織功能，PostgreSQL 是唯一持久化資料庫。

## 已完成功能

### Customer

- 註冊、登入與 30 分鐘 HS256 JWT Access Token
- 公開商品瀏覽、關鍵字搜尋、分類篩選、白名單排序、分頁與商品詳情
- 查詢自己的購物車、新增品項、修改數量與刪除品項
- 由購物車建立訂單、查看自己的訂單列表與詳情
- `PENDING_PAYMENT → PAID` 模擬付款

### Admin

- 商品列表、搜尋、分類、active 狀態篩選、排序與分頁
- 商品新增、編輯、重新啟用、絕對庫存設定與 soft delete
- 全部訂單列表、狀態篩選、詳情與合法狀態轉移
- 取消 `PENDING_PAYMENT`、`PAID`、`PROCESSING` 訂單並回補庫存

## 核心安全與一致性設計

- Spring Security 採 stateless JWT 與 CUSTOMER／ADMIN 角色授權；未匹配的 request 最終由 `anyRequest().denyAll()` 拒絕。
- Customer 與 Admin API 分離；目前使用者 ID 只取自 JWT principal，前端 role 只用於導覽與 route guard 體驗。
- Cart 顯示價格不是結帳依據。下單時 backend 重新讀取商品價格、active 狀態與庫存，並計算小計及總額。
- 下單依 `productId` 排序取得 Product pessimistic lock；扣庫存、建立 Order／OrderItem 與清空 CartItem 位於同一 transaction，失敗全部 rollback。
- OrderItem 保存商品 ID、名稱、單價、數量與小計 snapshot；歷史訂單不讀取目前 Product 名稱或價格。
- 取消訂單鎖定 Order，再依 `productId` 排序鎖定 Product；狀態更新與庫存回補同 transaction，重複或併發取消最多回補一次。
- 商品 DELETE 是 `active=false` 的 soft delete；inactive 商品不出現在公開 Catalog，但 Admin 仍可查詢。
- PostgreSQL 是實際 runtime 與 Compose 驗收資料庫。H2 只用於部分 JPA mapping／query 測試，不視為 PostgreSQL 等價替代。

詳細設計見 [architecture](docs/architecture.md)、[ERD](docs/erd.md) 與 [API 規格](docs/api-spec.md)。

## 本機啟動

### 前置需求

- Docker Engine／Docker Desktop 與 Docker Compose
- 直接開發另需 Java 21、Maven 3.9、Node.js 24 與 npm

### Docker Compose 完整啟動

複製環境變數範例，將 placeholder 密碼與 JWT secret 改成只供本機使用的值：

```powershell
Copy-Item .env.example .env
```

macOS／Linux 使用 `cp .env.example .env`。`.env.example` 只提供變數名稱與範例值；`.env` 已被 Git 忽略。`JWT_SECRET` 必須至少 32 bytes。

```bash
docker compose up --build -d
docker compose ps
```

預設服務位置；可由 `.env` 的 `FRONTEND_PORT`、`BACKEND_PORT`、`POSTGRES_PORT` 覆寫：

| 服務 | 預設位置 |
| --- | --- |
| Vue／Nginx | `http://localhost:3000` |
| 經 Nginx 代理的 health | `http://localhost:3000/actuator/health` |
| Backend health | `http://localhost:8080/actuator/health` |
| Swagger UI | `http://localhost:8080/swagger-ui/index.html` |
| OpenAPI JSON | `http://localhost:8080/v3/api-docs` |
| PostgreSQL | `localhost:5432` |

Swagger UI 與 OpenAPI JSON 由 backend port 直接提供，不經 Nginx 或 Vite proxy。文件可匿名讀取，但受保護 API 仍在 runtime 驗證 JWT 與角色。

停止服務：

```bash
docker compose down
```

需要連同本機 PostgreSQL named volume 一起清除時才使用：

```bash
docker compose down -v
```

### 直接開發

先啟動 PostgreSQL：

```bash
docker compose up -d postgres
```

Root `.env` 不會由直接執行的 Spring Boot 自動載入；請在 shell 設定 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、`JWT_SECRET`。其中 DB URL 的 database／port、username、password 必須分別與 `.env` 的 `POSTGRES_DB`／`POSTGRES_PORT`、`POSTGRES_USER`、`POSTGRES_PASSWORD` 一致。以下範例假設只更換預設 password：

```powershell
$env:DB_URL = "jdbc:postgresql://localhost:5432/shopflow"
$env:DB_USERNAME = "shopflow"
$env:DB_PASSWORD = "<same-as-POSTGRES_PASSWORD>"
$env:JWT_SECRET = "<at-least-32-random-characters>"

cd backend
mvn spring-boot:run
```

另一個 terminal 啟動 frontend：

```bash
cd frontend
npm ci
npm run dev
```

Vite 預設位於 `http://localhost:5173`，只在本機開發時將 `/api`、`/actuator` proxy 至 `http://localhost:8080`。

## 前端路由

| 權限 | Routes |
| --- | --- |
| 公開 | `/`、`/login`、`/register`、`/products`、`/products/:id` |
| CUSTOMER | `/cart`、`/orders`、`/orders/:id` |
| ADMIN | `/admin/products`、`/admin/products/new`、`/admin/products/:id/edit`、`/admin/orders`、`/admin/orders/:id` |

Vue Router guard 只提供 UX；真正的授權邊界是 backend Spring Security。

## Backend API 摘要

| 模組 | 主要 endpoints |
| --- | --- |
| Authentication | `POST /api/auth/register`、`POST /api/auth/login` |
| Catalog | `GET /api/categories`、`GET /api/products`、`GET /api/products/{productId}` |
| Cart | `GET /api/cart`、`POST/PATCH/DELETE /api/cart/items...` |
| Customer Orders | `POST/GET /api/orders...`、`POST /api/orders/{orderId}/pay` |
| Admin Products | `GET/POST/PUT/PATCH/DELETE /api/admin/products...` |
| Admin Orders | `GET /api/admin/orders...`、`PATCH /api/admin/orders/{orderId}/status` |

完整 request／response、錯誤碼、分頁、排序與角色規格請以 [Swagger UI](http://localhost:8080/swagger-ui/index.html) 及 [API 規格](docs/api-spec.md) 為準。OpenAPI tags 為 Authentication、Catalog、Cart、Customer Orders、Admin Products、Admin Orders、Health。

## 測試與品質檢查

Frontend：

```bash
cd frontend
npm test
npm run lint
npm run type-check
npm run build
```

Backend：

```bash
cd backend
mvn test
```

Repository 未提供 Maven Wrapper，因此使用本機 Maven。

## 文件

- [架構說明](docs/architecture.md)：runtime、模組、安全邊界與交易資料流。
- [ERD](docs/erd.md)：資料表、欄位、關聯與約束。
- [API 規格](docs/api-spec.md)：endpoint、權限、DTO、狀態與錯誤。
- [開發路線圖](docs/development-roadmap.md)：已完成 backend 階段的實作與驗收順序。
- [AGENTS.md](AGENTS.md)：程式、測試與安全規則。

## 尚未實作

- Refresh Token
- Logout API
- Token blacklist
- 真實金流
- Payment table
- Customer cancel order
- 微服務拆分
