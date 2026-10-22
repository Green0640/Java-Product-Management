package org.example.productmanagement.infrastructure.persistence;

import org.example.productmanagement.entity.Product;
import org.example.productmanagement.repository.ProductRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * {@link ProductRepository} 的 JPA 實作：負責領域實體與 JPA Entity 之間的轉換。
 */
@Repository
public class JpaProductRepository implements ProductRepository {

    private final SpringDataProductJpaRepository jpaRepository;

    public JpaProductRepository(SpringDataProductJpaRepository jpaRepository) {
        this.jpaRepository = jpaRepository;
    }

    @Override
    public Product save(Product product) {
        return jpaRepository.save(ProductJpaEntity.fromDomain(product)).toDomain();
    }

    @Override
    public Optional<Product> findById(Long id) {
        return jpaRepository.findById(id).map(ProductJpaEntity::toDomain);
    }

    @Override
    public List<Product> findAll() {
        return jpaRepository.findAll().stream()
                .map(ProductJpaEntity::toDomain)
                .toList();
    }

    @Override
    public void deleteById(Long id) {
        jpaRepository.deleteById(id);
    }

    @Override
    public boolean decreaseStock(Long productId, int quantity) {
        return jpaRepository.decreaseStock(productId, quantity) == 1;
    }
}