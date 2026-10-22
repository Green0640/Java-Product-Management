package org.example.productmanagement.service;

import org.example.productmanagement.entity.Product;
import org.example.productmanagement.repository.ProductRepository;
import org.example.productmanagement.service.exception.ProductNotFoundException;
import org.example.productmanagement.service.model.CreateProductCommand;
import org.example.productmanagement.service.model.ProductResult;
import org.example.productmanagement.service.model.UpdateProductCommand;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 商品業務邏輯：只依賴 {@link ProductRepository} 介面，不知道底層是 JPA 還是其他技術。
 */
@Service
@Transactional
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public ProductResult create(CreateProductCommand command) {
        Product product = new Product(
                null,
                command.name(),
                command.description(),
                command.price(),
                command.stock(),
                command.onSale()
        );
        return ProductResult.from(productRepository.save(product));
    }

    @Transactional(readOnly = true)
    public ProductResult getById(Long id) {
        return ProductResult.from(findExisting(id));
    }

    @Transactional(readOnly = true)
    public List<ProductResult> getAll() {
        return productRepository.findAll().stream()
                .map(ProductResult::from)
                .toList();
    }

    /**
     * 部分更新：command 中為 null 的欄位沿用目前的值。
     */
    public ProductResult update(UpdateProductCommand command) {
        Product existing = findExisting(command.id());
        Product product = new Product(
                existing.getId(),
                valueOrCurrent(command.name(), existing.getName()),
                valueOrCurrent(command.description(), existing.getDescription()),
                valueOrCurrent(command.price(), existing.getPrice()),
                valueOrCurrent(command.stock(), existing.getStock()),
                valueOrCurrent(command.onSale(), existing.isOnSale())
        );
        return ProductResult.from(productRepository.save(product));
    }

    public void delete(Long id) {
        findExisting(id);
        productRepository.deleteById(id);
    }

    private static <T> T valueOrCurrent(T value, T current) {
        return value != null ? value : current;
    }

    private Product findExisting(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }
}