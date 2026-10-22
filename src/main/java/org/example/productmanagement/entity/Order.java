package org.example.productmanagement.entity;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * 訂單領域實體：純 Java 物件，不依賴任何框架（JPA / Spring）。
 */
public class Order {

    private final UUID orderId;
    private final String idempotencyKey;
    private final String requestHash;
    private final List<OrderItem> items;
    private final OrderStatus status;
    private final BigDecimal totalAmount;
    private final Instant createdAt;

    public Order(UUID orderId, String idempotencyKey, String requestHash, List<OrderItem> items,
                 OrderStatus status, BigDecimal totalAmount, Instant createdAt) {
        this.orderId = orderId;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.items = List.copyOf(items);
        this.status = status;
        this.totalAmount = totalAmount;
        this.createdAt = createdAt;
    }

    /**
     * 建立新訂單：orderId 由領域自行產生，狀態為 CREATED，總額由各明細小計加總而來。
     * 建立時間截到毫秒，避免資料庫精度較低時，重送請求讀回的時間與第一次回應不一致。
     */
    public static Order create(String idempotencyKey, String requestHash, List<OrderItem> items) {
        BigDecimal totalAmount = items.stream()
                .map(OrderItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return new Order(UUID.randomUUID(), idempotencyKey, requestHash, items,
                OrderStatus.CREATED, totalAmount, Instant.now().truncatedTo(ChronoUnit.MILLIS));
    }

    public UUID getOrderId() {
        return orderId;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public String getRequestHash() {
        return requestHash;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
