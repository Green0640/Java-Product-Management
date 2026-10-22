package org.example.productmanagement.service.model;

import org.example.productmanagement.entity.Product;

import java.math.BigDecimal;

public record ProductResult(
        Long id,
        String name,
        String description,
        BigDecimal price,
        Integer stock,
        boolean onSale
) {
    public static ProductResult from(Product product) {
        return new ProductResult(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStock(),
                product.isOnSale()
        );
    }
}