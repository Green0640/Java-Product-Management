# 設計架構文件

> 本文件說明系統**為什麼這樣設計**。啟動方式、API 用法與測試執行請見 [README.md](README.md)。

## 1. 目標
- 功能需求:管理商品，並提供下單功能的API系統。
- 非功能需求:避免超賣、重複下單的防護、輸入驗證與錯誤處理、取消訂單與庫存回補等。

| 需求 | 狀態 | 對應設計 |
|---|---|---|
| 商品管理（建立、查詢、部分更新、刪除） | 已完成 | [3. 架構](#3架構) |
| 下單（多項商品、計算總額） | 已完成 | [4.4](#44-整個下單流程在同一個交易)、[4.5](#45-訂單明細保存下單當下的快照) |
| 避免超賣 | 已完成 | [4.1](#41-扣庫存條件式原子更新)、[4.3](#43-多項商品依-productid-排序後扣庫存) |
| 重複下單的防護 | 已完成 | [4.2](#42-冪等idempotency-key--請求內容雜湊) |
| 輸入驗證與錯誤處理 | 已完成 | [6. 錯誤處理](#6錯誤處理) |
| 取消訂單與庫存回補 | 尚未實作 | `OrderStatus` 已預留 `CANCELLED`，見 [2. 領域模型](#2領域模型) |

## 2.領域模型

| 實體 | 說明                                                   |
|---|------------------------------------------------------|
| `Product` | 商品。`isOnSale = false` 的商品不能下單                        |
| `Order` | 訂單。`orderId` 由 `Order.create` 自行產生，總額由明細小計加總，不接受外部傳入 |
| `OrderItem` | 訂單明細。保存的是下單當下的商品名稱與單價。                               |
| `OrderStatus` | 訂單狀態。目前只會產生 `CREATED`                                |

**設計重點**：

- 領域實體都是純 Java 物件，不加任何 JPA 或 Spring 註解。
- `Order`、`OrderItem` 的欄位都是 `final`，建立後不能修改，明細清單也用 `List.copyOf` 複製，外部無法改動。
- `OrderItem` 和 `Product` 之間只靠 `productId` 關聯。商品之後改價、改名或被刪除，既有訂單都不受影響。


## 3.架構

### 分層與依賴方向

```
        HTTP 請求
            │
┌───────────▼────────────┐
│ controller / dto       │  Web 層：HTTP ↔ Service 轉換、輸入驗證、錯誤回應
└───────────┬────────────┘
            │ Command / Result
┌───────────▼────────────┐
│ service                │  業務邏輯：下單流程、冪等、扣庫存、交易邊界
└───────────┬────────────┘
            │ 依賴介面（Output Port）
┌───────────▼────────────┐        ┌──────────────────────────┐
│ repository（介面）       │ ◄─────│ infrastructure           │
└────────────────────────┘  實作  │ JPA + H2                 │
                                  └──────────────────────────┘
        entity：純 Java 領域物件，各層共用，不依賴任何框架
```

依賴一律由外往內：`service` 只認得 `repository` 介面，不知道底下是 JPA 還是其他資料庫。

### 目錄結構

```
src/main/java/org/example/productmanagement
├── controller/                 # Web 輸入轉接器
│   ├── ProductController
│   ├── OrderController
│   └── GlobalExceptionHandler  # 所有例外 → 統一的錯誤回應格式
├── dto/                        # HTTP 請求 / 回應格式
│   ├── *Request / *Response
│   ├── ApiErrorResponse        # 錯誤回應 { code, message }
│   └── ErrorCode               # 與前端約定的錯誤代碼
├── service/                    # 業務邏輯
│   ├── ProductService
│   ├── OrderService
│   ├── model/                  # Service 的輸入（Command）與輸出（Result）
│   └── exception/              # 業務例外
├── entity/                     # 領域實體：Product、Order、OrderItem、OrderStatus
├── repository/                 # 儲存庫介面（Output Port）
└── infrastructure/persistence/ # JPA 實作
    ├── *JpaEntity              # 資料表對應物件，與領域實體互相轉換
    ├── SpringData*JpaRepository  # Spring Data 介面（package-private，不對外暴露）
    └── Jpa*Repository          # 實作 repository 介面
```

### 資料轉換流程

```
Request (dto) → Command (service/model) → Entity (entity) → JpaEntity (infrastructure)
Response (dto) ← Result (service/model) ← Entity (entity) ← JpaEntity (infrastructure)
```

每一層都有自己的資料格式。修改 API 欄位不會影響資料表結構，修改資料表也不會影響 API。

### 為什麼採用整潔架構

- **可替換性**：更換資料庫時，只需在 `infrastructure` 新增一組實作，`service` 與 `controller` 不必修改。
- **可測試性**：`service` 的單元測試直接換上記憶體版 repository，不需要啟動 Spring 或資料庫。
- **變更隔離**：API、業務邏輯、資料表各自有自己的資料格式，改其中一層不會牽動其他層。

## 4.關鍵設計決策

### 4.1 扣庫存：條件式原子更新

**問題**：兩個請求同時讀到「庫存 = 1」，都判斷足夠，各自扣 1，結果庫存變成 -1。

| 方案 | 優點 | 缺點 |
|---|---|---|
| 先 SELECT 再 UPDATE（無鎖） | 最簡單 | 會超賣 |
| 悲觀鎖 `SELECT ... FOR UPDATE` | 語意清楚 | 多一次查詢，鎖持有時間較長 |
| 樂觀鎖 `@Version` | 不佔用鎖 | 衝突時要重試，熱門商品的重試率很高 |
| **條件式 UPDATE（採用）** | 檢查與扣除在同一條 SQL 完成，不需要重試 | 無法得知失敗原因，需要再查一次 |

以 Spring Data JPA 的 `@Modifying` + `@Query`（JPQL）實作，見 `SpringDataProductJpaRepository.decreaseStock`：

```java
@Modifying(flushAutomatically = true, clearAutomatically = true)
@Query("update ProductJpaEntity p set p.stock = p.stock - :quantity "
        + "where p.id = :id and p.onSale = true and p.stock >= :quantity")
int decreaseStock(@Param("id") Long id, @Param("quantity") int quantity);
```

- 這是 JPA 的 bulk update，不會先把 Entity 載入再修改，而是直接對資料庫送出一條 UPDATE。
- `flushAutomatically = true`：執行前先把 persistence context 中尚未寫入的變更 flush 出去。
- `clearAutomatically = true`：執行後清除 persistence context，避免之後讀到快取中的舊庫存值。

影響列數為 1 代表成功，0 代表失敗。失敗時再查一次商品，區分是「不存在」、「未上架」還是「庫存不足」，回傳對應的錯誤碼。

### 4.2 冪等：Idempotency-Key + 請求內容雜湊

**問題**：使用者重複點擊或網路逾時重送，不能建立兩筆訂單。

訂單會保存 `Idempotency-Key`，以及請求內容（`items`）的 SHA-256 雜湊值。

| 情況 | 結果 |
|---|---|
| 新的 Key | 建立訂單，回傳 `201` |
| 相同 Key、相同內容（重送） | 回傳第一次建立的訂單（`201`），不會重複建立、不會重複扣庫存 |
| 相同 Key、不同內容 | `422 IDEMPOTENCY_KEY_REUSED` |
| 相同 Key 的兩個請求同時送達 | 後到者回傳 `409 IDEMPOTENCY_KEY_CONFLICT`，稍後重試即可取得已建立的訂單 |

- **為什麼要存雜湊**：只存 Key 的話，客戶端如果誤把同一個 Key 用在不同訂單上，會拿回內容不符的舊訂單。比對雜湊可以把這種錯誤回報給客戶端。
- **為什麼要靠唯一約束**：「先查有沒有這個 Key」和「寫入」之間有時間差，兩個請求可能同時查到「沒有」。資料庫對 `idempotency_key` 的唯一約束保證只會寫入一筆，程式裡的查詢只是讓一般的重送能快速回應。
- **為什麼重送也回 `201`**：客戶端不必區分「第一次」和「重送」，用同一套邏輯處理就能拿到相同的結果。
- **下單失敗不保存 Key**：失敗時整個交易 rollback，Key 不會寫入，補足條件後（例如商品補貨）可以用同一個 Key 重試。
- **限制**：判斷「內容相同」時會考慮商品的排列順序，同樣的商品換了順序會被視為不同內容。Key 目前永久保存，也沒有依使用者區分範圍。

### 4.3 多項商品依 productId 排序後扣庫存

**問題**：條件式 UPDATE 會鎖住商品列直到交易結束。訂單 1 依序扣 `[A, B]`、訂單 2 依序扣 `[B, A]`，兩者可能各持有一把鎖、互相等待對方，造成 deadlock。

**決定**：扣庫存前一律依 `productId` 排序。所有交易都以相同順序上鎖，就不會形成循環等待。

### 4.4 整個下單流程在同一個交易

`OrderService.create` 包在同一個 `@Transactional` 內：檢查冪等 → 讀取商品 → 扣庫存 → 寫入訂單。任一項商品失敗都會 rollback，已扣的庫存也會還原。

`JpaOrderRepository.save` 使用 `saveAndFlush`，讓唯一約束衝突在 repository 內就被偵測到並轉成 `IdempotencyKeyConflictException`，不必等到 commit 才以 Spring 的例外拋出。

### 4.5 訂單明細保存下單當下的快照

訂單明細保存下單當下的商品名稱與單價，之後商品改價不影響既有訂單。`order_items.product_id` 刻意不設外鍵，商品被刪除時不會被既有訂單擋住。

### 4.6 金額與時間

- 金額一律使用 `BigDecimal`，資料庫欄位為 `DECIMAL(19, 2)`，避免浮點數誤差。
- `createdAt` 截到毫秒。資料庫的時間精度可能比 `Instant.now()` 低，截斷後，重送請求讀回的時間會和第一次回應一致。

### 4.7 商品更新使用 PATCH

只修改有傳入的欄位，只想改價格的客戶端不必先讀取完整商品再整筆送回。

**限制**：

- `null` 代表不修改，所以無法把 `description` 清成 `null`。
- 更新時會把整筆商品寫回，包含 `stock`。如果 PATCH 和下單同時發生，可能用舊的庫存值蓋掉剛扣除的數量。之後可以為商品加上 `@Version` 樂觀鎖，或讓 PATCH 只更新有變動的欄位。

## 5.資料模型

| 設計 | 理由 |
|---|---|
| `orders.idempotency_key` 唯一約束 `uk_orders_idempotency_key` | 由資料庫保證同一個 Key 只寫入一筆，見 [4.2](#42-冪等idempotency-key--請求內容雜湊) |
| `orders.request_hash` 長度 64 | 存放 SHA-256 的十六進位字串 |
| 訂單 ID 用 UUID | 由領域物件自行產生，不依賴資料庫序號；對外公開時也不會透露訂單量，或讓人猜出其他訂單的 ID |
| 商品 ID 用自動遞增 | 商品是後台管理的資料，可讀性比較重要 |
| `order_items.product_id` 不設外鍵 | 明細是快照，商品被刪除後訂單仍然要保留，見 [4.5](#45-訂單明細保存下單當下的快照) |
| `status` 以字串儲存 | 新增或調整 enum 的順序時，不會讓既有資料的意義跑掉 |

## 6.錯誤處理

錯誤代碼的完整清單與回應格式見 [README 的錯誤回應](README.md#錯誤回應)，這裡只說明設計原則。

### 分工

```
service 拋出領域例外 ──► GlobalExceptionHandler ──► { code, message } + HTTP 狀態碼
                              ▲
Spring MVC 內建錯誤 ──────────┘（405、415、JSON 格式錯誤、驗證失敗等）
```

- `service` 只拋出領域例外（`ProductNotFoundException`、`IdempotencyKeyReusedException` 等），不知道 HTTP 狀態碼。
- `ErrorCode` 集中定義錯誤代碼、HTTP 狀態碼與訊息，新增錯誤時只需要改一個地方。
- `GlobalExceptionHandler` 繼承 `ResponseEntityExceptionHandler`，所以 Spring MVC 本身的錯誤也用同一種格式回傳，前端只需要處理一種錯誤格式。
- 未預期的例外一律回 `500 INTERNAL_ERROR`，詳細內容只寫進 log，不回傳給客戶端，避免洩漏內部資訊。

### 輸入驗證

- 格式驗證放在 `dto`（Bean Validation）：必填、`≥ 0`、`≥ 1`、Header 長度等。
- 業務規則放在 `service`：商品是否存在、是否上架、庫存是否足夠。
- PATCH 的欄位都是選填，有傳入時才檢查格式，例如 `name` 有傳時不可為空白。

### 狀態碼的選擇

| 狀態碼 | 使用時機 |
|---|---|
| `400` | 請求格式或參數錯誤；商品未上架、庫存不足 |
| `404` | 商品或訂單不存在 |
| `409` | 相同 Key 的請求正在處理，**稍後重試**就能成功 |
| `422` | Key 已用於不同內容，**重試也不會成功**，客戶端必須換一個 Key |

`409` 和 `422` 分開，是為了讓客戶端能判斷「要不要重試」。