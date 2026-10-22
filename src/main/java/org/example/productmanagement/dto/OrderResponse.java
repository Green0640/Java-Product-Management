package org.example.productmanagement.dto;

import org.example.productmanagement.entity.OrderStatus;
import org.example.productmanagement.service.model.OrderResult;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID orderId,
        OrderStatus status,
        List<Item> items,
        BigDecimal totalAmount,
        Instant createdAt
) {
    public static OrderResponse from(OrderResult result) {
        return new OrderResponse(
                result.orderId(),
                result.status(),
                result.items().stream().map(Item::from).toList(),
                result.totalAmount(),
                result.createdAt()
        );
    }

    public record Item(
            Long productId,
            String productName,
            BigDecimal unitPrice,
            int quantity,
            BigDecimal subtotal
    ) {
        static Item from(OrderResult.Item item) {
            return new Item(
                    item.productId(),
                    item.productName(),
                    item.unitPrice(),
                    item.quantity(),
                    item.subtotal()
            );
        }
    }
}
