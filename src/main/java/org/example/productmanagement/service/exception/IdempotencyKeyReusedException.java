package org.example.productmanagement.service.exception;

/**
 * 同一個 Idempotency-Key 被用在內容不同的請求上。
 */
public class IdempotencyKeyReusedException extends RuntimeException {

    public IdempotencyKeyReusedException(String idempotencyKey) {
        super("Idempotency-Key already used with a different request: " + idempotencyKey);
    }
}
