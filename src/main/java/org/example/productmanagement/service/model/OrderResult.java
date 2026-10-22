package org.example.productmanagement.service.model;

import org.example.productmanagement.entity.Order;
import org.example.productmanagement.entity.OrderItem;
import org.example.productmanagement.entity.OrderStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResult(
        UUID orderId,
        OrderStatus status,
        List<Item> items,
        BigDecimal totalAmount,
        Instant createdAt
) {
    public static OrderResult from(Order order) {
        return new OrderResult(
                order.getOrderId(),
                order.getStatus(),
                order.getItems().stream().map(Item::from).toList(),
                order.getTotalAmount(),
                order.getCreatedAt()
        );
    }

    public record Item(
            Long productId,
            String productName,
            BigDecimal unitPrice,
            int quantity,
            BigDecimal subtotal
    ) {
        static Item from(OrderItem item) {
            return new Item(
                    item.getProductId(),
                    item.getProductName(),
                    item.getUnitPrice(),
                    item.getQuantity(),
                    item.getSubtotal()
            );
        }
    }
}
