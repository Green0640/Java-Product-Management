package org.example.productmanagement.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import org.example.productmanagement.service.model.UpdateProductCommand;

import java.math.BigDecimal;

/**
 * PATCH 部分更新：每個欄位都是選填，沒傳（null）代表不修改；有傳時才檢查格式。
 */
public record UpdateProductRequest(
        @Pattern(regexp = "(?s).*\\S.*") String name,
        String description,
        @PositiveOrZero BigDecimal price,
        @PositiveOrZero Integer stock,
        Boolean onSale
) {
    public UpdateProductCommand toCommand(Long id) {
        return new UpdateProductCommand(id, name, description, price, stock, onSale);
    }
}
