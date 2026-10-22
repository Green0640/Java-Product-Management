package org.example.productmanagement.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA 介面：只給 {@link JpaProductRepository} 使用，不對外暴露。
 */
interface SpringDataProductJpaRepository extends JpaRepository<ProductJpaEntity, Long> {
}