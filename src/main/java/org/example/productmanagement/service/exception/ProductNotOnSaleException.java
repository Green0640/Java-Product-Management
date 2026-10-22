package org.example.productmanagement.service.exception;

public class ProductNotOnSaleException extends RuntimeException {
    public ProductNotOnSaleException(String message) {
        super(message);
    }
}
