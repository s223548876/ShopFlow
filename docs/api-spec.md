# ShopFlow REST API 規格

## 共通規則

- Base path：`/api`
- Media type：`application/json`
- 時間：ISO 8601 UTC，例如 `2026-07-02T08:30:00Z`
- 金額：JSON number，後端使用 `BigDecimal`，資料庫使用 `NUMERIC(12,2)`
- 驗證：受保護 API 使用 `Authorization: Bearer <access-token>`
- 角色：`CUSTOMER`、`ADMIN`，每位使用者只有一個角色
- Request 中的 `userId`、`role`、Cart／OrderItem 商品價格及訂單總價一律忽略或拒絕，不能作為授權或結帳計價依據；ADMIN 商品建立／更新 DTO 的 `price` 是受授權的商品管理欄位

### 分頁格式

分頁 query 預設為 `page=0&size=20`，`size` 必須介於 1 到 100。`sort` 格式為 `field,direction`。

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0,
  "first": true,
  "last": true
}
```

### 統一錯誤格式

```json
{
  "timestamp": "2026-07-02T08:30:00Z",
  "status": 400,
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "path": "/api/auth/register",
  "fieldErrors": [
    {
      "field": "email",
      "message": "must be a valid email"
    }
  ]
}
```

`fieldErrors` 沒有內容時回傳空陣列。主要狀態碼：

| HTTP | 用途 |
| --- | --- |
| 400 | 格式、欄位或分頁參數錯誤 |
| 401 | 未登入、JWT 無效或已過期 |
| 403 | 已登入但角色不足 |
| 404 | 資源不存在、公開商品已停用，或資源不屬於目前使用者 |
| 409 | email 重複、庫存不足、商品不可購買或訂單狀態衝突 |

## Authentication

### `POST /api/auth/register`

建立 CUSTOMER。Request 不接受 `role` 或 `userId`。

權限：公開。

```json
{
  "email": "alice@example.com",
  "password": "correct-horse-42",
  "displayName": "Alice"
}
```

驗證：email 合法且不重複；password 長度 8–72 字元；displayName 長度 1–100。

成功：`201 Created`

```json
{
  "id": 101,
  "email": "alice@example.com",
  "displayName": "Alice",
  "role": "CUSTOMER",
  "createdAt": "2026-07-02T08:30:00Z"
}
```

錯誤：`400 VALIDATION_ERROR`、`409 EMAIL_ALREADY_EXISTS`。

### `POST /api/auth/login`

權限：公開。

```json
{
  "email": "alice@example.com",
  "password": "correct-horse-42"
}
```

成功：`200 OK`

```json
{
  "accessToken": "<jwt>",
  "tokenType": "Bearer",
  "expiresIn": 1800
}
```

第一版只發出 30 分鐘 Access Token，不提供 refresh token。登入資訊錯誤統一回傳 `401 INVALID_CREDENTIALS`，不透露 email 是否存在。

## Categories

### `GET /api/categories`

權限：公開。第一版分類由 Flyway 初始資料維護。

成功：`200 OK`

```json
[
  { "id": 1, "name": "Electronics" },
  { "id": 2, "name": "Books" }
]
```

## Public products

公開商品 endpoint 永遠只查詢 `active = true`。

### `GET /api/products`

權限：公開。

Query：

| 參數 | 必填 | 說明 |
| --- | --- | --- |
| `q` | 否 | 商品名稱或描述的大小寫不敏感關鍵字 |
| `categoryId` | 否 | 單一分類 ID |
| `page` | 否 | 從 0 開始，預設 0 |
| `size` | 否 | 1–100，預設 20 |
| `sort` | 否 | 允許 `name`、`price`、`createdAt`；預設 `createdAt,desc` |

範例：`GET /api/products?q=keyboard&categoryId=1&page=0&size=20&sort=price,asc`

成功：`200 OK`

```json
{
  "content": [
    {
      "id": 501,
      "name": "Mechanical Keyboard",
      "price": 89.90,
      "stock": 12,
      "category": { "id": 1, "name": "Electronics" }
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "first": true,
  "last": true
}
```

錯誤：`400 INVALID_PAGE_REQUEST`、`400 INVALID_SORT`。

### `GET /api/products/{productId}`

權限：公開。

成功：`200 OK`

```json
{
  "id": 501,
  "name": "Mechanical Keyboard",
  "description": "Hot-swappable mechanical keyboard.",
  "price": 89.90,
  "stock": 12,
  "category": { "id": 1, "name": "Electronics" }
}
```

不存在或 `active = false`：`404 PRODUCT_NOT_FOUND`。公開 response 不包含 `active`，因為只可能回傳 active 商品。

## Cart

所有 Cart endpoint 都要求 CUSTOMER，並從 JWT principal 取得 userId。使用其他使用者的 CartItem ID 時回傳 404。

### `GET /api/cart`

成功：`200 OK`

```json
{
  "id": 301,
  "items": [
    {
      "id": 401,
      "productId": 501,
      "productName": "Mechanical Keyboard",
      "currentUnitPrice": 89.90,
      "quantity": 2,
      "subtotal": 179.80,
      "available": true
    }
  ],
  "estimatedTotal": 179.80
}
```

`currentUnitPrice`、`subtotal` 與 `estimatedTotal` 只供畫面顯示，不是下單依據。若商品後來被停用，品項仍可顯示但 `available` 為 false。

### `POST /api/cart/items`

新增 active 商品。加入購物車不保留庫存。

```json
{
  "productId": 501,
  "quantity": 2
}
```

quantity 必須介於 1 到 999，且加入當下不得超過目前庫存。

成功：`201 Created`

```json
{
  "id": 401,
  "productId": 501,
  "productName": "Mechanical Keyboard",
  "currentUnitPrice": 89.90,
  "quantity": 2,
  "subtotal": 179.80,
  "available": true
}
```

同一商品已存在時回傳 `409 CART_ITEM_ALREADY_EXISTS`；inactive 商品回傳 `409 PRODUCT_UNAVAILABLE`；庫存不足回傳 `409 INSUFFICIENT_STOCK`。

### `PATCH /api/cart/items/{itemId}`

```json
{
  "quantity": 3
}
```

quantity 必須介於 1 到 999；刪除請使用 DELETE，不以 0 代表刪除。

成功：`200 OK`，response 使用完整 CartItemResponse：

```json
{
  "id": 401,
  "productId": 501,
  "productName": "Mechanical Keyboard",
  "currentUnitPrice": 89.90,
  "quantity": 3,
  "subtotal": 269.70,
  "available": true
}
```

錯誤：`404 CART_ITEM_NOT_FOUND`、`409 PRODUCT_UNAVAILABLE`、`409 INSUFFICIENT_STOCK`。

### `DELETE /api/cart/items/{itemId}`

成功：`204 No Content`。不存在或不屬於目前使用者：`404 CART_ITEM_NOT_FOUND`。

## Customer orders and mock payment

所有 endpoint 都要求 CUSTOMER。Order 的 userId 取自 JWT principal，查詢其他使用者訂單時回傳 404。

### `POST /api/orders`

由目前使用者的整個購物車建立訂單。Controller 不讀取 request body；額外傳入的商品清單、價格、總價或 userId 不會參與訂單內容、授權或計價。

成功：`201 Created`

```json
{
  "id": 701,
  "status": "PENDING_PAYMENT",
  "totalAmount": 179.80,
  "paidAt": null,
  "createdAt": "2026-07-02T09:00:00Z",
  "items": [
    {
      "productId": 501,
      "productName": "Mechanical Keyboard",
      "unitPrice": 89.90,
      "quantity": 2,
      "subtotal": 179.80
    }
  ]
}
```

後端必須在同一交易中依 productId 排序取得悲觀鎖，重新檢查 active、價格與庫存，接著完成：

1. 扣減庫存。
2. 建立 `PENDING_PAYMENT` Order。
3. 建立包含 `productId`、`productName`、`unitPrice`、`quantity`、`subtotal` 的 OrderItem 快照。
4. 清空 CartItem。

任一步失敗時，庫存、Order、OrderItem 與購物車全部 rollback。

錯誤：`409 CART_EMPTY`、`409 PRODUCT_UNAVAILABLE`、`409 INSUFFICIENT_STOCK`。失敗 response 不得包含部分訂單。

### `POST /api/orders/{orderId}/pay`

沒有 request body。只允許訂單本人將 `PENDING_PAYMENT` 轉為 `PAID`；模擬付款固定成功。

成功：`200 OK`

```json
{
  "id": 701,
  "status": "PAID",
  "totalAmount": 179.80,
  "paidAt": "2026-07-02T09:02:00Z",
  "createdAt": "2026-07-02T09:00:00Z",
  "items": [
    {
      "productId": 501,
      "productName": "Mechanical Keyboard",
      "unitPrice": 89.90,
      "quantity": 2,
      "subtotal": 179.80
    }
  ]
}
```

錯誤：`404 ORDER_NOT_FOUND`、`409 INVALID_ORDER_TRANSITION`。不建立 Payment entity，也不處理付款卡資料。

### `GET /api/orders`

Query：`page`、`size`、`sort`，只允許 `createdAt,asc`、`createdAt,desc`，預設 `createdAt,desc`。只回傳目前 JWT 使用者的訂單。

成功：`200 OK`

```json
{
  "content": [
    {
      "id": 701,
      "status": "PAID",
      "totalAmount": 179.80,
      "itemCount": 2,
      "createdAt": "2026-07-02T09:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "first": true,
  "last": true
}
```

`OrderSummaryResponse.itemCount` 是所有 OrderItem `quantity` 的總和，不是訂單明細列數。

### `GET /api/orders/{orderId}`

成功：`200 OK`，response 使用完整 OrderResponse，歷史顯示以 OrderItem 快照為準。不存在或不屬於目前使用者：`404 ORDER_NOT_FOUND`。

Customer API 沒有取消訂單 endpoint；取消與庫存回補只由 ADMIN 狀態管理處理。

## Admin products

所有 endpoint 都要求 ADMIN。ADMIN 查詢可以看到 inactive 商品。

### `GET /api/admin/products`

Query：

| 參數 | 必填 | 說明 |
| --- | --- | --- |
| `q` | 否 | 商品名稱或描述的大小寫不敏感關鍵字；空白視為未指定 |
| `categoryId` | 否 | 單一分類 ID |
| `active` | 否 | `true` 或 `false`；未指定時同時回傳 active 與 inactive |
| `page` | 否 | 從 0 開始，預設 0 |
| `size` | 否 | 1–100，預設 20 |
| `sort` | 否 | 白名單如下；預設 `createdAt,desc` |

Sort 白名單：`name,asc`、`name,desc`、`price,asc`、`price,desc`、`createdAt,asc`、`createdAt,desc`。

成功：`200 OK`

```json
{
  "content": [
    {
      "id": 501,
      "name": "Mechanical Keyboard",
      "description": "Hot-swappable mechanical keyboard.",
      "price": 89.90,
      "stock": 12,
      "active": false,
      "category": { "id": 1, "name": "Electronics" },
      "createdAt": "2026-07-01T08:00:00Z",
      "updatedAt": "2026-07-02T08:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1,
  "first": true,
  "last": true
}
```

錯誤：`400 INVALID_PAGE_REQUEST`、`400 INVALID_SORT`、`400 VALIDATION_ERROR`。

### `GET /api/admin/products/{productId}`

成功：`200 OK`，使用包含 `active` 的 AdminProductResponse。inactive 商品仍回傳 200；真正不存在才回傳 `404 PRODUCT_NOT_FOUND`。

### `POST /api/admin/products`

```json
{
  "categoryId": 1,
  "name": "Mechanical Keyboard",
  "description": "Hot-swappable mechanical keyboard.",
  "price": 89.90,
  "stock": 20
}
```

`categoryId` 必須為正數且分類存在；name 必填、trim 後不可空白且不超過 200 字元；description 必填且不超過 5000 字元；price 必須大於 0，最多 10 位整數與 2 位小數；stock 必須為非負整數。

成功：`201 Created`，回傳 AdminProductResponse，`active` 預設 true。

錯誤：`400 VALIDATION_ERROR`、`400 MALFORMED_REQUEST`、`404 CATEGORY_NOT_FOUND`。

### `PUT /api/admin/products/{productId}`

完整更新商品基本資料及 active 狀態；庫存改用獨立 endpoint。

```json
{
  "categoryId": 1,
  "name": "Mechanical Keyboard V2",
  "description": "Updated description.",
  "price": 99.90,
  "active": true
}
```

成功：`200 OK`，回傳 AdminProductResponse。此 endpoint 可重新啟用 inactive 商品，但不能修改 stock。

欄位驗證與建立商品相同；`active` 必填。錯誤：`400 VALIDATION_ERROR`、`400 MALFORMED_REQUEST`、`404 PRODUCT_NOT_FOUND`、`404 CATEGORY_NOT_FOUND`。

### `PATCH /api/admin/products/{productId}/stock`

設定絕對庫存數量，不接受負數。

```json
{
  "quantity": 25
}
```

成功：`200 OK`

```json
{
  "productId": 501,
  "stock": 25,
  "updatedAt": "2026-07-02T10:00:00Z"
}
```

錯誤：`400 VALIDATION_ERROR`、`400 MALFORMED_REQUEST`、`404 PRODUCT_NOT_FOUND`。

### `DELETE /api/admin/products/{productId}`

將 `active` 設為 false，不物理刪除。成功及重複停用皆回傳 `204 No Content`；不存在回傳 `404 PRODUCT_NOT_FOUND`。停用後商品不再出現在公開 Catalog，但既有 OrderItem snapshot 與歷史訂單不受影響。

## Admin orders

所有 endpoint 都要求 ADMIN。

### `GET /api/admin/orders`

Query：`status`、`page`、`size`、`sort`。`status` 可使用六種 OrderStatus；sort 只允許 `createdAt,asc`、`createdAt,desc`，預設 `createdAt,desc`。成功回傳包含所有使用者訂單的 PageResponse<OrderSummaryResponse>。

範例：`GET /api/admin/orders?status=PAID&page=0&size=20`

### `GET /api/admin/orders/{orderId}`

成功：`200 OK`，回傳 AdminOrderResponse；不存在時回傳 `404 ORDER_NOT_FOUND`。

```json
{
  "id": 701,
  "user": {
    "id": 101,
    "email": "alice@example.com",
    "displayName": "Alice"
  },
  "status": "PAID",
  "totalAmount": 179.80,
  "paidAt": "2026-07-02T09:02:00Z",
  "createdAt": "2026-07-02T09:00:00Z",
  "items": [
    {
      "productId": 501,
      "productName": "Mechanical Keyboard",
      "unitPrice": 89.90,
      "quantity": 2,
      "subtotal": 179.80
    }
  ]
}
```

歷史內容只使用 OrderItem 快照；Product 目前名稱、價格或 active 狀態不得改變此 response。

### `PATCH /api/admin/orders/{orderId}/status`

```json
{
  "status": "PROCESSING"
}
```

合法轉移：

| 目前狀態 | 可轉移至 |
| --- | --- |
| `PENDING_PAYMENT` | `CANCELLED` |
| `PAID` | `PROCESSING`、`CANCELLED` |
| `PROCESSING` | `SHIPPED`、`CANCELLED` |
| `SHIPPED` | `COMPLETED` |
| `COMPLETED` | 無 |
| `CANCELLED` | 無 |

`PENDING_PAYMENT → PAID` 只能由訂單本人呼叫模擬付款 endpoint 完成，ADMIN 不可代替 Customer 付款。相同狀態的重複 request 回傳目前 AdminOrderResponse，不重複執行副作用。

取消規則：

1. 在交易中取得 Order 寫入鎖。
2. 若已為 `CANCELLED`，回傳目前結果，不回補庫存。
3. 若為 `SHIPPED` 或 `COMPLETED`，回傳 `409 INVALID_ORDER_TRANSITION`，不回補庫存。
4. 只有 `PENDING_PAYMENT`、`PAID`、`PROCESSING` 可以取消；依 productId 排序鎖定商品並依 OrderItem quantity 回補。
5. 回補及狀態更新必須在同一交易 commit；失敗全部 rollback。

成功：`200 OK`，回傳完整 AdminOrderResponse。重複或併發取消最多只會回補一次。

## OpenAPI

實作時以 controller、DTO 與 validation annotations 產生 OpenAPI 3 文件，Swagger UI 應描述 Bearer authentication、角色需求、所有 request/response schema 及上述錯誤。Markdown 規格是商業契約，產生的 OpenAPI 必須與本文件一致。

- Swagger UI：`http://localhost:8080/swagger-ui/index.html`
- OpenAPI JSON：`http://localhost:8080/v3/api-docs`

兩者皆可匿名讀取，並直接由 backend 提供，不經 Nginx 或 Vite proxy。公開文件不會改變 API 的 runtime 授權：Cart 與 Customer Orders 仍要求 CUSTOMER，Admin Products 與 Admin Orders 仍要求 ADMIN。Swagger UI 以 `bearerAuth` HTTP Bearer security scheme 傳送 JWT Access Token；Authentication、Catalog 與 Health operations 不要求 token。
