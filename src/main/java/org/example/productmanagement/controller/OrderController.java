package org.example.productmanagement.controller;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.example.productmanagement.dto.CreateOrderRequest;
import org.example.productmanagement.dto.OrderResponse;
import org.example.productmanagement.service.OrderService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Web 輸入轉接器：只負責 HTTP <-> Service 的轉換，不含任何業務邏輯。
 */
@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * 同一個 Idempotency-Key 重送相同內容時，回傳第一次建立的訂單（一樣是 201）。
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public OrderResponse create(@RequestHeader("Idempotency-Key") @NotBlank @Size(max = 255) String idempotencyKey,
                                @Valid @RequestBody CreateOrderRequest request) {
        return OrderResponse.from(orderService.create(request.toCommand(idempotencyKey)));
    }

    @GetMapping("/{orderId}")
    public OrderResponse getById(@PathVariable UUID orderId) {
        return OrderResponse.from(orderService.getById(orderId));
    }
}
