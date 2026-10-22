package org.example.productmanagement.service;

import org.example.productmanagement.entity.Order;
import org.example.productmanagement.repository.OrderRepository;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 測試用的記憶體版 OrderRepository：讓 Service 測試不需要啟動 Spring 或連資料庫。
 */
class InMemoryOrderRepository implements OrderRepository {

    private final Map<UUID, Order> store = new LinkedHashMap<>();

    @Override
    public Order save(Order order) {
        store.put(order.getOrderId(), order);
        return order;
    }

    @Override
    public Optional<Order> findById(UUID orderId) {
        return Optional.ofNullable(store.get(orderId));
    }

    @Override
    public Optional<Order> findByIdempotencyKey(String idempotencyKey) {
        return store.values().stream()
                .filter(order -> order.getIdempotencyKey().equals(idempotencyKey))
                .findFirst();
    }

    int count() {
        return store.size();
    }
}
