package org.example.productmanagement.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.example.productmanagement.entity.Product;

import java.math.BigDecimal;

/**
 * 資料表對應物件：只在 infrastructure 內使用，與領域實體 {@link Product} 互相轉換。
 */
@Entity
@Table(name = "products")
public class ProductJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private Integer stock;

    @Column(name = "on_sale", nullable = false)
    private boolean onSale;

    protected ProductJpaEntity() {
    }

    private ProductJpaEntity(Long id, String name, String description, BigDecimal price, Integer stock, boolean onSale) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.price = price;
        this.stock = stock;
        this.onSale = onSale;
    }

    public static ProductJpaEntity fromDomain(Product product) {
        return new ProductJpaEntity(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStock(),
                product.isOnSale()
        );
    }

    public Product toDomain() {
        return new Product(id, name, description, price, stock, onSale);
    }
}