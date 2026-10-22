package org.example.productmanagement.infrastructure.persistence;

import org.example.productmanagement.entity.Order;
import org.example.productmanagement.repository.OrderRepository;
import org.example.productmanagement.service.exception.IdempotencyKeyConflictException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * {@link OrderRepository} 的 JPA 實作：負責領域實體與 JPA Entity 之間的轉換。
 */
@Repository
public class JpaOrderRepository implements OrderRepository {

    private final SpringDataOrderJpaRepository jpaRepository;

    public JpaOrderRepository(SpringDataOrderJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    /**
     * 立即 flush，讓 Idempotency-Key 的唯一鍵衝突在這裡就被偵測到，並轉成領域例外。
     */
    @Override
    public Order save(Order order) {
        try {
            return jpaRepository.saveAndFlush(OrderJpaEntity.fromDomain(order)).toDomain();
        } catch (DataIntegrityViolationException e) {
            if (isIdempotencyKeyViolation(e)) {
                throw new IdempotencyKeyConflictException(order.getIdempotencyKey(), e);
            }
            throw e;
        }
    }

    @Override
    public Optional<Order> findById(UUID orderId) {
        return jpaRepository.findById(orderId).map(OrderJpaEntity::toDomain);
    }

    @Override
    public Optional<Order> findByIdempotencyKey(String idempotencyKey) {
        return jpaRepository.findByIdempotencyKey(idempotencyKey).map(OrderJpaEntity::toDomain);
    }

    private static boolean isIdempotencyKeyViolation(DataIntegrityViolationException e) {
        String message = e.getMostSpecificCause().getMessage();
        return message != null && message.toLowerCase(Locale.ROOT).contains(OrderJpaEntity.IDEMPOTENCY_KEY_CONSTRAINT);
    }
}
