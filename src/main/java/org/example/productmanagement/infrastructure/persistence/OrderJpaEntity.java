package org.example.productmanagement.infrastructure.persistence;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.example.productmanagement.entity.Order;
import org.example.productmanagement.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 訂單資料表對應物件：只在 infrastructure 內使用，與領域實體 {@link Order} 互相轉換。
 */
@Entity
@Table(name = "orders", uniqueConstraints = @UniqueConstraint(
        name = OrderJpaEntity.IDEMPOTENCY_KEY_CONSTRAINT, columnNames = "idempotency_key"))
public class OrderJpaEntity {

    static final String IDEMPOTENCY_KEY_CONSTRAINT = "uk_orders_idempotency_key";

    @Id
    @Column(name = "order_id")
    private UUID orderId;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "request_hash", nullable = false, length = 64)
    private String requestHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    // 明細數量不多，直接一起載入，避免在交易外轉換成領域物件時發生 LazyInitializationException
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @JoinColumn(name = "order_id", nullable = false)
    @OrderBy("id")
    private List<OrderItemJpaEntity> items = new ArrayList<>();

    protected OrderJpaEntity() {
    }

    public static OrderJpaEntity fromDomain(Order order) {
        OrderJpaEntity entity = new OrderJpaEntity();
        entity.orderId = order.getOrderId();
        entity.idempotencyKey = order.getIdempotencyKey();
        entity.requestHash = order.getRequestHash();
        entity.status = order.getStatus();
        entity.totalAmount = order.getTotalAmount();
        entity.createdAt = order.getCreatedAt();
        entity.items = new ArrayList<>(order.getItems().stream().map(OrderItemJpaEntity::fromDomain).toList());
        return entity;
    }

    public Order toDomain() {
        return new Order(
                orderId,
                idempotencyKey,
                requestHash,
                items.stream().map(OrderItemJpaEntity::toDomain).toList(),
                status,
                totalAmount,
                createdAt
        );
    }
}
