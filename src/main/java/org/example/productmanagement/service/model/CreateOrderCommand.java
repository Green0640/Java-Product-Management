package org.example.productmanagement.service.model;

import java.util.List;

public record CreateOrderCommand(
        String idempotencyKey,
        List<Item> items
) {
    public record Item(Long productId, int quantity) {
    }
}
