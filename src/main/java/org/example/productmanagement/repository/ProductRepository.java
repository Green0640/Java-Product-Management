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

    /**
     * 原子性扣庫存：只有商品上架中且庫存足夠時才扣除，避免同時下單造成超賣。
     * 成功扣除後，該商品會被鎖住直到交易結束，同一筆交易扣多項商品時要依固定順序呼叫，避免 deadlock。
     *
     * @return 扣除成功回傳 true；庫存不足、商品未上架或不存在回傳 false
     */
    boolean decreaseStock(Long productId, int quantity);
}