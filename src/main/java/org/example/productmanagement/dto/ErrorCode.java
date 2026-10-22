package org.example.productmanagement.dto;

import org.springframework.http.HttpStatus;

/**
 * 與前端約定的錯誤代碼：前端依 {@code code} 判斷錯誤種類，{@code message} 可直接顯示給使用者。
 */
public enum ErrorCode {

    // 通用請求錯誤
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "請求參數驗證失敗"),
    MALFORMED_REQUEST(HttpStatus.BAD_REQUEST, "請求內容格式錯誤"),
    INVALID_PARAMETER(HttpStatus.BAD_REQUEST, "參數型別錯誤"),
    INVALID_REQUEST(HttpStatus.BAD_REQUEST, "請求無法處理"),
    RESOURCE_NOT_FOUND(HttpStatus.NOT_FOUND, "找不到請求的路徑"),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "不支援的 HTTP 方法"),
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "不支援的 Content-Type"),
    MISSING_HEADER(HttpStatus.BAD_REQUEST, "缺少必要的 Header"),

    // 商品
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "找不到商品"),
    PRODUCT_INSUFFICIENT_STOCK(HttpStatus.BAD_REQUEST, "商品庫存不足"),
    PRODUCT_NOT_ON_SALE(HttpStatus.BAD_REQUEST, "商品未上架"),

    // 訂單
    ORDER_NOT_FOUND(HttpStatus.NOT_FOUND, "找不到訂單"),
    IDEMPOTENCY_KEY_REUSED(HttpStatus.UNPROCESSABLE_CONTENT, "此 Idempotency-Key 已用於內容不同的請求"),
    IDEMPOTENCY_KEY_CONFLICT(HttpStatus.CONFLICT, "相同 Idempotency-Key 的請求正在處理中，請稍後重試"),

    // 系統
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "系統發生錯誤，請稍後再試");

    private final HttpStatus status;
    private final String message;

    ErrorCode(HttpStatus status, String message) {
        this.status = status;
        this.message = message;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}