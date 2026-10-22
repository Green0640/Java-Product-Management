package org.example.productmanagement.infrastructure.persistence;

import org.example.productmanagement.entity.Order;
import org.example.productmanagement.entity.OrderItem;
import org.example.productmanagement.entity.OrderStatus;
import org.example.productmanagement.service.exception.IdempotencyKeyConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(JpaOrderRepository.class)
class JpaOrderRepositoryTest {

    @Autowired
    private JpaOrderRepository repository;

    @Test
    void savePersistsOrderWithItems() {
        Order saved = repository.save(newOrder("key-1"));

        assertThat(repository.findById(saved.getOrderId())).get().satisfies(found -> {
            assertThat(found.getIdempotencyKey()).isEqualTo("key-1");
            assertThat(found.getRequestHash()).isEqualTo("hash");
            assertThat(found.getStatus()).isEqualTo(OrderStatus.CREATED);
            assertThat(found.getTotalAmount()).isEqualByComparingTo("4979");
            assertThat(found.getCreatedAt()).isNotNull();
            assertThat(found.getItems()).extracting(OrderItem::getProductName).containsExactly("Keyboard", "Mouse");
            assertThat(found.getItems()).extracting(OrderItem::getQuantity).containsExactly(2, 2);
        });
    }

    @Test
    void findByIdempotencyKey() {
        Order saved = repository.save(newOrder("key-1"));

        assertThat(repository.findByIdempotencyKey("key-1")).get()
                .extracting(Order::getOrderId)
                .isEqualTo(saved.getOrderId());
        assertThat(repository.findByIdempotencyKey("other")).isEmpty();
        assertThat(repository.findById(UUID.randomUUID())).isEmpty();
    }

    @Test
    void duplicateIdempotencyKeyThrowsConflict() {
        repository.save(newOrder("key-1"));

        assertThatThrownBy(() -> repository.save(newOrder("key-1")))
                .isInstanceOf(IdempotencyKeyConflictException.class);
    }

    private static Order newOrder(String key) {
        return Order.create(key, "hash", List.of(
                new OrderItem(1L, "Keyboard", new BigDecimal("1990"), 2),
                new OrderItem(2L, "Mouse", new BigDecimal("499.50"), 2)
        ));
    }
}
