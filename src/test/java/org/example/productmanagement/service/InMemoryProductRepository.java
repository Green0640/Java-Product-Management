package org.example.productmanagement.service;

import org.example.productmanagement.entity.Product;
import org.example.productmanagement.repository.ProductRepository;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 測試用的記憶體版 ProductRepository：讓 Service 測試不需要啟動 Spring 或連資料庫。
 */
class InMemoryProductRepository implements ProductRepository {

    private final Map<Long, Product> store = new LinkedHashMap<>();
    private long nextId = 1;

    @Override
    public Product save(Product product) {
        Long id = product.getId() != null ? product.getId() : nextId++;
        Product saved = new Product(id, product.getName(), product.getDescription(),
                product.getPrice(), product.getStock(), product.isOnSale());
        store.put(id, saved);
        return saved;
    }

    @Override
    public Optional<Product> findById(Long id) {
        return Optional.ofNullable(store.get(id));
    }

    @Override
    public List<Product> findAll() {
        return new ArrayList<>(store.values());
    }

    @Override
    public void deleteById(Long id) {
        store.remove(id);
    }

    @Override
    public boolean decreaseStock(Long productId, int quantity) {
        Product product = store.get(productId);
        if (product == null || !product.isOnSale() || product.getStock() < quantity) {
            return false;
        }
        save(new Product(product.getId(), product.getName(), product.getDescription(),
                product.getPrice(), product.getStock() - quantity, product.isOnSale()));
        return true;
    }
}
