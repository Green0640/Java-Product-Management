package org.example.productmanagement.dto;

import org.example.productmanagement.service.model.ProductResult;

import java.math.BigDecimal;

public record ProductResponse(
        Long id,
        String name,
        String description,
        BigDecimal price,
        Integer stock,
        boolean onSale
) {
    public static ProductResponse from(ProductResult result) {
        return new ProductResponse(
                result.id(),
                result.name(),
                result.description(),
                result.price(),
                result.stock(),
                result.onSale()
        );
    }
}