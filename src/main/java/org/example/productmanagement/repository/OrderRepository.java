package org.example.productmanagement.repository;

import org.example.productmanagement.entity.Order;

import java.util.Optional;
import java.util.UUID;

/**
 * 訂單儲存庫介面（Output Port）：由 service 使用，實作放在 infrastructure。
 */
public interface OrderRepository {

    /**
     * 儲存訂單。若 Idempotency-Key 已被其他訂單使用（例如同時送出的重複請求），
     * 實作必須丟出 {@link org.example.productmanagement.service.exception.IdempotencyKeyConflictException}。
     */
    Order save(Order order);

    Optional<Order> findById(UUID orderId);

    Optional<Order> findByIdempotencyKey(String idempotencyKey);
}
