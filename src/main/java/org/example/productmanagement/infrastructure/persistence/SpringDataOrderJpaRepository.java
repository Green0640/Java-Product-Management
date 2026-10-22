package org.example.productmanagement.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA 介面：只給 {@link JpaOrderRepository} 使用，不對外暴露。
 */
interface SpringDataOrderJpaRepository extends JpaRepository<OrderJpaEntity, UUID> {

    Optional<OrderJpaEntity> findByIdempotencyKey(String idempotencyKey);
}
