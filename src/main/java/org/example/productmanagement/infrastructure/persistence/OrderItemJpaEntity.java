package org.example.productmanagement.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.example.productmanagement.entity.OrderItem;

import java.math.BigDecimal;

/**
 * 訂單明細資料表對應物件：只在 infrastructure 內使用，與領域實體 {@link OrderItem} 互相轉換。
 */
@Entity
@Table(name = "order_items")
public class OrderItemJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private int quantity;

    protected OrderItemJpaEntity() {
    }

    private OrderItemJpaEntity(Long productId, String productName, BigDecimal unitPrice, int quantity) {
        this.productId = productId;
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
    }

    public static OrderItemJpaEntity fromDomain(OrderItem item) {
        return new OrderItemJpaEntity(item.getProductId(), item.getProductName(), item.getUnitPrice(), item.getQuantity());
    }

    public OrderItem toDomain() {
        return new OrderItem(productId, productName, unitPrice, quantity);
    }
}
