package org.example.productmanagement.service;

import org.example.productmanagement.service.exception.ProductNotFoundException;
import org.example.productmanagement.service.model.CreateProductCommand;
import org.example.productmanagement.service.model.ProductResult;
import org.example.productmanagement.service.model.UpdateProductCommand;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 不啟動 Spring、不連資料庫：用記憶體版的 ProductRepository 單獨測試業務流程。
 */
class ProductServiceTest {

    private ProductService service;

    @BeforeEach
    void setUp() {
        service = new ProductService(new InMemoryProductRepository());
    }

    @Test
    void createReturnsSavedProductWithId() {
        ProductResult result = service.create(new CreateProductCommand("Keyboard", "Mechanical", new BigDecimal("1990"), 10, true));

        assertThat(result.id()).isNotNull();
        assertThat(result.name()).isEqualTo("Keyboard");
        assertThat(result.onSale()).isTrue();
    }

    @Test
    void updateReplacesExistingProduct() {
        ProductResult created = service.create(new CreateProductCommand("Mouse", null, new BigDecimal("500"), 5, false));

        ProductResult updated = service.update(new UpdateProductCommand(created.id(), "Mouse Pro", null, new BigDecimal("800"), 3, true));

        assertThat(updated.name()).isEqualTo("Mouse Pro");
        assertThat(service.getAll()).hasSize(1);
    }

    @Test
    void updateOnlyChangesProvidedFields() {
        ProductResult created = service.create(new CreateProductCommand("Keyboard", "Mechanical", new BigDecimal("1990"), 10, true));

        ProductResult updated = service.update(new UpdateProductCommand(created.id(), null, null, new BigDecimal("1490"), null, null));

        assertThat(updated).isEqualTo(new ProductResult(created.id(), "Keyboard", "Mechanical", new BigDecimal("1490"), 10, true));
    }

    @Test
    void updateWithNoFieldsKeepsProductUnchanged() {
        ProductResult created = service.create(new CreateProductCommand("Mouse", null, new BigDecimal("500"), 5, false));

        ProductResult updated = service.update(new UpdateProductCommand(created.id(), null, null, null, null, null));

        assertThat(updated).isEqualTo(created);
    }

    @Test
    void deleteRemovesProduct() {
        ProductResult created = service.create(new CreateProductCommand("Monitor", null, new BigDecimal("6000"), 2, false));

        service.delete(created.id());

        assertThat(service.getAll()).isEmpty();
    }

    @Test
    void missingProductThrowsNotFound() {
        assertThatThrownBy(() -> service.getById(99L)).isInstanceOf(ProductNotFoundException.class);
        assertThatThrownBy(() -> service.update(new UpdateProductCommand(99L, "X", null, BigDecimal.ONE, 1, false)))
                .isInstanceOf(ProductNotFoundException.class);
        assertThatThrownBy(() -> service.delete(99L)).isInstanceOf(ProductNotFoundException.class);
    }
}
