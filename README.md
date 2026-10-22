# 電商商品訂購系統

商品與訂單管理的 REST API，採用整潔架構（Clean Architecture）分層。

- 商品：建立、查詢、部分更新（PATCH）、刪除
- 訂單：一次訂購多項商品並計算總額、以 `Idempotency-Key` 防止重複下單、併發下單不超賣

| 項目 | 版本 / 技術 |
|---|---|
| Java | 17 |
| 框架 | Spring Boot 4.1.1（Web MVC、Validation、Data JPA） |
| 資料庫 | H2（預設記憶體模式） |
| 建置工具 | Maven（內附 Maven Wrapper，不需另外安裝） |
| 測試 | JUnit 5、Mockito、MockMvc、AssertJ |

---

## 設計說明

採用整潔架構分層，依賴一律由外往內：`controller` → `service` → `repository`（介面）← `infrastructure`（JPA 實作）。

分層、目錄結構、防超賣與冪等等關鍵設計的說明，請見 [Design.md](Design.md)。

---

## 啟動方式

### 環境需求

需要 JDK 17 以上，並設定 `JAVA_HOME`。

### 啟動

在專案根目錄執行：

```bash
# macOS / Linux / Git Bash
./mvnw spring-boot:run
```

```powershell
# Windows PowerShell
.\mvnw.cmd spring-boot:run
```

看到 `Tomcat started on port 8080` 即啟動完成，API 位址為 `http://localhost:8080`。

如果 `JAVA_HOME` 沒有設定或指向錯誤的 JDK，可以在執行前臨時指定：

```powershell
$env:JAVA_HOME = "C:\path\to\jdk-17"; .\mvnw.cmd spring-boot:run
```

也可以直接在 IDE 中執行 `ProductmanagementApplication` 的 `main` 方法。

### 資料庫

預設使用 H2 記憶體資料庫，**重啟後資料會清空**。`ddl-auto=update` 會在啟動時自動建立資料表，不需要手動建表。

若未來需給正式的資料庫使用，推薦使用MySQL之類的關聯式資料庫，才能進行進一步的優化與擴充.
但如此會需要寫新的 Repository 實作，當然 Service 與 Controller 不必修改。


### H2 Console

啟動後開啟 `http://localhost:8080/h2-console`：

| 欄位 | 值 |
|---|---|
| JDBC URL | `jdbc:h2:mem:productdb`（檔案模式則填 `application.properties` 中的 URL） |
| User Name | `sa` |
| Password | （空白） |

---

## API 文件

- 所有請求與回應皆為 JSON，請求需帶 `Content-Type: application/json`。
- 金額欄位（`price`、`unitPrice`、`subtotal`、`totalAmount`）為數字，最多兩位小數。
- 時間欄位為 ISO-8601 UTC 格式，例如 `2026-10-23T08:00:00.123Z`。

### API 一覽

| Method | Path | 說明 | 成功狀態碼 |
|---|---|---|---|
| `POST` | `/products` | 建立商品 | `201` |
| `GET` | `/products` | 查詢所有商品 | `200` |
| `GET` | `/products/{id}` | 查詢單一商品 | `200` |
| `PATCH` | `/products/{id}` | 部分更新商品 | `200` |
| `DELETE` | `/products/{id}` | 刪除商品 | `204` |
| `POST` | `/orders` | 建立訂單 | `201` |
| `GET` | `/orders/{orderId}` | 查詢訂單 | `200` |

### 商品

#### 商品回應格式

```json
{
  "id": 1,
  "name": "Keyboard",
  "description": "Mechanical",
  "price": 1990.00,
  "stock": 10,
  "onSale": true
}
```

#### `POST /products` 建立商品

| 欄位 | 型別 | 必填 | 規則 |
|---|---|---|---|
| `name` | string | ✔ | 不可為空白 |
| `description` | string | | |
| `price` | number | ✔ | ≥ 0 |
| `stock` | integer | ✔ | ≥ 0 |
| `onSale` | boolean | ✔ | 是否上架；未上架的商品無法下單 |

```json
{ "name": "Keyboard", "description": "Mechanical", "price": 1990, "stock": 10, "onSale": true }
```

回應 `201`，內容為商品回應格式。

#### `GET /products` 查詢所有商品

回應 `200`，內容為商品回應格式的陣列。

#### `GET /products/{id}` 查詢單一商品

回應 `200`，內容為商品回應格式。商品不存在時回傳 `404 PRODUCT_NOT_FOUND`。

#### `PATCH /products/{id}` 部分更新商品

所有欄位皆為選填，**沒有傳的欄位維持原值**，有傳的欄位才會檢查格式。

| 欄位 | 型別 | 規則 |
|---|---|---|
| `name` | string | 有傳時不可為空白 |
| `description` | string | |
| `price` | number | ≥ 0 |
| `stock` | integer | ≥ 0 |
| `onSale` | boolean | |

```json
{ "price": 1790, "onSale": false }
```

回應 `200`，內容為更新後的商品。商品不存在時回傳 `404 PRODUCT_NOT_FOUND`。

> 傳入 `null` 等同於不修改，因此目前無法透過 PATCH 把 `description` 清空。

#### `DELETE /products/{id}` 刪除商品

回應 `204`，沒有內容。商品不存在時回傳 `404 PRODUCT_NOT_FOUND`。

### 訂單

#### 訂單回應格式

```json
{
  "orderId": "3f2a9c4e-8b1d-4e6f-9a7c-2d5b8e1f0a34",
  "status": "CREATED",
  "items": [
    { "productId": 1, "productName": "Keyboard", "unitPrice": 1990.00, "quantity": 2, "subtotal": 3980.00 },
    { "productId": 2, "productName": "Mouse", "unitPrice": 499.50, "quantity": 1, "subtotal": 499.50 }
  ],
  "totalAmount": 4479.50,
  "createdAt": "2026-10-23T08:00:00.123Z"
}
```

| 欄位 | 說明 |
|---|---|
| `orderId` | 訂單編號（UUID） |
| `status` | 訂單狀態：`CREATED`、`PAID`、`CANCELLED`。新訂單一律為 `CREATED` |
| `items[].productName` / `unitPrice` | 下單當下的商品名稱與單價 |
| `items[].subtotal` | `unitPrice × quantity` |
| `totalAmount` | 所有明細 `subtotal` 的總和 |

明細的順序與請求中 `items` 的順序相同。

#### `POST /orders` 建立訂單

**Header**

| Header | 必填 | 說明 |
|---|---|---|
| `Idempotency-Key` | ✔ | 由前端產生的唯一值（建議使用 UUID），最多 255 字。重送同一筆訂單時須使用相同的 Key |

**Body**

| 欄位 | 型別 | 必填 | 規則 |
|---|---|---|---|
| `items` | array | ✔ | 至少一項 |
| `items[].productId` | integer | ✔ | 商品 id |
| `items[].quantity` | integer | ✔ | ≥ 1 |

```http
POST /orders
Content-Type: application/json
Idempotency-Key: 7f3c9a2e-5b8d-4c1a-9e6f-2d4b8a1c0e57

{ "items": [ { "productId": 1, "quantity": 2 }, { "productId": 2, "quantity": 1 } ] }
```

回應 `201`，內容為訂單回應格式。

| 錯誤情況 | 狀態碼 | code |
|---|---|---|
| 沒有帶 `Idempotency-Key` | `400` | `MISSING_HEADER` |
| `Idempotency-Key` 為空白或超過 255 字、`items` 為空、`quantity` < 1 等 | `400` | `VALIDATION_FAILED` |
| 商品不存在 | `404` | `PRODUCT_NOT_FOUND` |
| 商品未上架 | `400` | `PRODUCT_NOT_ON_SALE` |
| 庫存不足 | `400` | `PRODUCT_INSUFFICIENT_STOCK` |
| 同一個 Key 已用於內容不同的請求 | `422` | `IDEMPOTENCY_KEY_REUSED` |
| 同一個 Key 的請求正在處理中 | `409` | `IDEMPOTENCY_KEY_CONFLICT` |

只要有任何一項商品失敗，整筆訂單都不會成立，也不會扣除任何庫存。

#### `GET /orders/{orderId}` 查詢訂單

回應 `200`，內容為訂單回應格式。

| 錯誤情況 | 狀態碼 | code |
|---|---|---|
| `orderId` 不是合法的 UUID | `400` | `INVALID_PARAMETER` |
| 訂單不存在 | `404` | `ORDER_NOT_FOUND` |

### 錯誤回應

所有錯誤都使用相同的格式：

```json
{ "code": "PRODUCT_INSUFFICIENT_STOCK", "message": "商品庫存不足" }
```

- `code`：與前端約定的錯誤代碼，前端應依此判斷錯誤種類。
- `message`：可直接顯示給使用者的中文說明。

| code | HTTP 狀態碼 | message |
|---|---|---|
| `VALIDATION_FAILED` | 400 | 請求參數驗證失敗 |
| `MALFORMED_REQUEST` | 400 | 請求內容格式錯誤 |
| `INVALID_PARAMETER` | 400 | 參數型別錯誤 |
| `INVALID_REQUEST` | 400 | 請求無法處理 |
| `MISSING_HEADER` | 400 | 缺少必要的 Header |
| `RESOURCE_NOT_FOUND` | 404 | 找不到請求的路徑 |
| `METHOD_NOT_ALLOWED` | 405 | 不支援的 HTTP 方法 |
| `UNSUPPORTED_MEDIA_TYPE` | 415 | 不支援的 Content-Type |
| `PRODUCT_NOT_FOUND` | 404 | 找不到商品 |
| `PRODUCT_INSUFFICIENT_STOCK` | 400 | 商品庫存不足 |
| `PRODUCT_NOT_ON_SALE` | 400 | 商品未上架 |
| `ORDER_NOT_FOUND` | 404 | 找不到訂單 |
| `IDEMPOTENCY_KEY_REUSED` | 422 | 此 Idempotency-Key 已用於內容不同的請求 |
| `IDEMPOTENCY_KEY_CONFLICT` | 409 | 相同 Idempotency-Key 的請求正在處理中，請稍後重試 |
| `INTERNAL_ERROR` | 500 | 系統發生錯誤，請稍後再試 |

### 用 curl 快速試用

```bash
# 建立兩個商品
curl -X POST http://localhost:8080/products -H "Content-Type: application/json" \
  -d '{"name":"Keyboard","description":"Mechanical","price":1990,"stock":10,"onSale":true}'
curl -X POST http://localhost:8080/products -H "Content-Type: application/json" \
  -d '{"name":"Mouse","description":"Wireless","price":499.5,"stock":5,"onSale":true}'

# 建立訂單
curl -X POST http://localhost:8080/orders -H "Content-Type: application/json" \
  -H "Idempotency-Key: test-001" \
  -d '{"items":[{"productId":1,"quantity":2},{"productId":2,"quantity":1}]}'

# 查詢訂單（替換成上一步回傳的 orderId）
curl http://localhost:8080/orders/<orderId>
```

使用 Postman 時，`Idempotency-Key` 可以填入內建變數 `{{$guid}}`，每次送出都會自動產生新的 UUID。

---

## 測試執行方式

### 執行全部測試

```bash
./mvnw test          # macOS / Linux / Git Bash
```

```powershell
.\mvnw.cmd test      # Windows PowerShell
```

### 執行指定的測試

```bash
./mvnw test -Dtest=OrderServiceTest                      # 單一測試類別
./mvnw test -Dtest=OrderServiceTest#createDecreasesStock # 單一測試方法
./mvnw test -Dtest='Order*'                              # 符合名稱的所有類別
```

PowerShell 中 `-D` 參數需要加上引號，例如 `.\mvnw.cmd test "-Dtest=OrderServiceTest"`。

測試報告輸出在 `target/surefire-reports/`。

### 測試結構

| 層級 | 測試類別 | 方式 | 驗證重點 |
|---|---|---|---|
| Service | `ProductServiceTest`、`OrderServiceTest` | 純單元測試，使用記憶體版 Repository（`InMemory*Repository`），不啟動 Spring | 業務規則：總額計算、冪等、庫存檢查、部分更新 |
| Controller | `ProductControllerCreateTest`、`ProductControllerUpdateTest`、`OrderControllerTest`、`GlobalExceptionHandlerTest` | `@WebMvcTest`，只啟動 Web 層，Service 以 `@MockitoBean` 取代 | HTTP 狀態碼、輸入驗證、Header 檢查、錯誤代碼與回應格式 |
| Infrastructure | `JpaProductRepositoryTest`、`JpaOrderRepositoryTest` | `@DataJpaTest`，連接 H2 | 領域實體與 JPA Entity 的轉換、條件式扣庫存、唯一鍵衝突 |
| 整合測試 | `OrderApiIntegrationTest` | `@SpringBootTest` + MockMvc，啟動完整應用程式 | HTTP → Service → 資料庫整條路線、交易 rollback、冪等重送 |
| 併發測試 | `OrderConcurrencyTest` | `@SpringBootTest`，多條執行緒同時下單 | 不超賣、多商品訂單不會 deadlock |
| 啟動 | `ProductmanagementApplicationTests` | `@SpringBootTest` | Spring Context 能正常啟動 |

整合測試與併發測試各自使用獨立的 H2 記憶體資料庫，不會影響開發用的資料。

---

## AI使用說明
規劃使用整潔式架構來完成系統，其餘使用Claude協助完成程式碼、測試與README文件，並且提供測試案例的建議。


