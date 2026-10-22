package org.example.productmanagement.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.example.productmanagement.service.model.CreateOrderCommand;

import java.util.List;

public record CreateOrderRequest(
        @NotEmpty List<@NotNull @Valid Item> items
) {
    public CreateOrderCommand toCommand(String idempotencyKey) {
        return new CreateOrderCommand(
                idempotencyKey,
                items.stream()
                        .map(item -> new CreateOrderCommand.Item(item.productId(), item.quantity()))
                        .toList()
        );
    }

    public record Item(
            @NotNull Long productId,
            @NotNull @Positive Integer quantity
    ) {
    }
}
