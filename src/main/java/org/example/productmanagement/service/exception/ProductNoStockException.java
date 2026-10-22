package org.example.productmanagement.service.exception;

public class ProductNoStockException extends RuntimeException {
    public ProductNoStockException(String message) {
        super(message);
    }
}
