package org.example.productmanagement.service.model;

import java.math.BigDecimal;

public record CreateProductCommand(
        String name,
        String description,
        BigDecimal price,
        Integer stock,
        boolean onSale
) {
}