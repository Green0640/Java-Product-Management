package org.example.productmanagement.entity;

import java.math.BigDecimal;

/**
 * 商品領域實體：純 Java 物件，不依賴任何框架（JPA / Spring）。
 */
public class Product {

    private Long id;
    private String name;
    private String description;
    private BigDecimal price;
    private Integer stock;
    private boolean isOnSale;

    public Product(Long id, String name, String description, BigDecimal price, Integer stock, boolean isOnSale) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.price = price;
        this.stock = stock;
        this.isOnSale = isOnSale;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public Integer getStock() {
        return stock;
    }

    public boolean isOnSale() {
        return isOnSale;
    }
}