package org.example.productmanagement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.example.productmanagement.service.model.CreateProductCommand;

import java.math.BigDecimal;

public record CreateProductRequest(
        @NotBlank String name,
        String description,
        @NotNull @PositiveOrZero BigDecimal price,
        @NotNull @PositiveOrZero Integer stock,
        @NotNull Boolean onSale
) {
    public CreateProductCommand toCommand() {
        return new CreateProductCommand(name, description, price, stock, onSale);
    }
}