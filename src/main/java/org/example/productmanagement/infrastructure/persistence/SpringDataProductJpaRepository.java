package org.example.productmanagement.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Spring Data JPA 介面：只給 {@link JpaProductRepository} 使用，不對外暴露。
 */
interface SpringDataProductJpaRepository extends JpaRepository<ProductJpaEntity, Long> {

    /**
     * 檢查與扣除在同一條 SQL 完成，資料庫會鎖住該列，同時下單也不會超賣。
     *
     * @return 受影響的列數：1 表示扣除成功，0 表示庫存不足、商品未上架或不存在
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update ProductJpaEntity p set p.stock = p.stock - :quantity "
            + "where p.id = :id and p.onSale = true and p.stock >= :quantity")
    int decreaseStock(@Param("id") Long id, @Param("quantity") int quantity);
}
