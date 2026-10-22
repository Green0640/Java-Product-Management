package org.example.productmanagement.repository;

import org.example.productmanagement.entity.Product;

import java.util.List;
import java.util.Optional;

/**
 * 商品儲存庫介面（Output Port）：由 service 使用，實作放在 infrastructure。
 */
public interface ProductRepository {

    Product save(Product product);

    Optional<Product> findById(Long id);

    List<Product> findAll();

    void deleteById(Long id);
}