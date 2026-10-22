package org.example.productmanagement.service.exception;

/**
 * 相同 Idempotency-Key 的請求同時送達，另一個請求已先寫入訂單。
 */
public class IdempotencyKeyConflictException extends RuntimeException {

    public IdempotencyKeyConflictException(String idempotencyKey, Throwable cause) {
        super("Concurrent request with the same Idempotency-Key: " + idempotencyKey, cause);
    }
}
