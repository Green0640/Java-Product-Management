package org.example.productmanagement.infrastructure.persistence;

import org.example.productmanagement.entity.Product;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JpaProductRepository.class)
class JpaProductRepositoryTest {

    @Autowired
    private JpaProductRepository repository;

    @Test
    void saveAssignsIdAndCanBeFound() {
        Product saved = repository.save(new Product(null, "Keyboard", "Mechanical", new BigDecimal("1990.00"), 10, true));

        assertThat(saved.getId()).isNotNull();
        assertThat(repository.findById(saved.getId()))
                .get()
                .satisfies(found -> {
                    assertThat(found.getName()).isEqualTo("Keyboard");
                    assertThat(found.getPrice()).isEqualByComparingTo("1990.00");
                    assertThat(found.getStock()).isEqualTo(10);
                    assertThat(found.isOnSale()).isTrue();
                });
    }

    @Test
    void saveWithExistingIdUpdates() {
        Product saved = repository.save(new Product(null, "Mouse", null, new BigDecimal("500"), 5, false));

        repository.save(new Product(saved.getId(), "Mouse Pro", null, new BigDecimal("800"), 3, true));

        assertThat(repository.findAll()).hasSize(1);
        assertThat(repository.findById(saved.getId())).get()
                .extracting(Product::getName)
                .isEqualTo("Mouse Pro");
    }

    @Test
    void deleteByIdRemovesProduct() {
        Product saved = repository.save(new Product(null, "Monitor", null, new BigDecimal("6000"), 2, false));

        repository.deleteById(saved.getId());

        assertThat(repository.findById(saved.getId())).isEmpty();
    }

    @Test
    void decreaseStockSucceedsWhenEnoughStock() {
        Product saved = repository.save(new Product(null, "Cable", null, new BigDecimal("100"), 5, true));

        assertThat(repository.decreaseStock(saved.getId(), 5)).isTrue();
        assertThat(repository.findById(saved.getId())).get()
                .extracting(Product::getStock)
                .isEqualTo(0);
    }

    @Test
    void decreaseStockFailsWithoutChangingStockWhenNotEnough() {
        Product saved = repository.save(new Product(null, "Cable", null, new BigDecimal("100"), 5, true));

        assertThat(repository.decreaseStock(saved.getId(), 6)).isFalse();
        assertThat(repository.findById(saved.getId())).get()
                .extracting(Product::getStock)
                .isEqualTo(5);
    }

    @Test
    void decreaseStockFailsWithoutChangingStockWhenNotOnSale() {
        Product saved = repository.save(new Product(null, "Cable", null, new BigDecimal("100"), 5, false));

        assertThat(repository.decreaseStock(saved.getId(), 1)).isFalse();
        assertThat(repository.findById(saved.getId())).get()
                .extracting(Product::getStock)
                .isEqualTo(5);
    }

    @Test
    void decreaseStockOfUnknownProductFails() {
        assertThat(repository.decreaseStock(999L, 1)).isFalse();
    }
}