package org.example.productmanagement.service.model;

import java.math.BigDecimal;

/**
 * 除了 id 以外，值為 null 的欄位代表不修改。
 */
public record UpdateProductCommand(
        Long id,
        String name,
        String description,
        BigDecimal price,
        Integer stock,
        Boolean onSale
) {
}
