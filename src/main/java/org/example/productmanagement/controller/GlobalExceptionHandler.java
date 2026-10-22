package org.example.productmanagement.controller;

import org.example.productmanagement.dto.ApiErrorResponse;
import org.example.productmanagement.dto.ErrorCode;
import org.example.productmanagement.service.exception.IdempotencyKeyConflictException;
import org.example.productmanagement.service.exception.IdempotencyKeyReusedException;
import org.example.productmanagement.service.exception.OrderNotFoundException;
import org.example.productmanagement.service.exception.ProductNoStockException;
import org.example.productmanagement.service.exception.ProductNotFoundException;
import org.example.productmanagement.service.exception.ProductNotOnSaleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.TypeMismatchException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * 把例外轉成統一的 {@link ApiErrorResponse}，讓 service 層不需要知道 HTTP 狀態碼與錯誤代碼。
 * 繼承 {@link ResponseEntityExceptionHandler}，連 Spring MVC 本身的錯誤（405、415、JSON 格式錯誤等）也用同一種格式回傳。
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ProductNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleProductNotFound(ProductNotFoundException ex) {
        return toResponse(ErrorCode.PRODUCT_NOT_FOUND);
    }

    @ExceptionHandler(ProductNoStockException.class)
    public ResponseEntity<ApiErrorResponse> handleProductNoStock(ProductNoStockException ex) {
        return toResponse(ErrorCode.PRODUCT_INSUFFICIENT_STOCK);
    }

    @ExceptionHandler(ProductNotOnSaleException.class)
    public ResponseEntity<ApiErrorResponse> handleProductNotOnSale(ProductNotOnSaleException ex) {
        return toResponse(ErrorCode.PRODUCT_NOT_ON_SALE);
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ApiErrorResponse> handleOrderNotFound(OrderNotFoundException ex) {
        return toResponse(ErrorCode.ORDER_NOT_FOUND);
    }

    @ExceptionHandler(IdempotencyKeyReusedException.class)
    public ResponseEntity<ApiErrorResponse> handleIdempotencyKeyReused(IdempotencyKeyReusedException ex) {
        return toResponse(ErrorCode.IDEMPOTENCY_KEY_REUSED);
    }

    @ExceptionHandler(IdempotencyKeyConflictException.class)
    public ResponseEntity<ApiErrorResponse> handleIdempotencyKeyConflict(IdempotencyKeyConflictException ex) {
        return toResponse(ErrorCode.IDEMPOTENCY_KEY_CONFLICT);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiErrorResponse> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return toResponse(ErrorCode.INTERNAL_ERROR);
    }

    /**
     * Spring MVC 內建錯誤的共同出口：把預設的 ProblemDetail 換成 {@link ApiErrorResponse}，HTTP 狀態碼維持 Spring 判斷的結果。
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex,
                                                             Object body,
                                                             HttpHeaders headers,
                                                             HttpStatusCode statusCode,
                                                             WebRequest request) {
        return ResponseEntity.status(statusCode)
                .headers(headers)
                .body(ApiErrorResponse.of(toErrorCode(ex, statusCode)));
    }

    private static ErrorCode toErrorCode(Exception ex, HttpStatusCode statusCode) {
        // 方法參數本身有驗證註解時（例如 @RequestHeader @NotBlank），Spring 會改丟 HandlerMethodValidationException
        if (ex instanceof MethodArgumentNotValidException || ex instanceof HandlerMethodValidationException) {
            return ErrorCode.VALIDATION_FAILED;
        }
        if (ex instanceof MissingRequestHeaderException) {
            return ErrorCode.MISSING_HEADER;
        }
        if (ex instanceof HttpMessageNotReadableException) {
            return ErrorCode.MALFORMED_REQUEST;
        }
        if (ex instanceof TypeMismatchException) {
            return ErrorCode.INVALID_PARAMETER;
        }
        if (ex instanceof NoResourceFoundException) {
            return ErrorCode.RESOURCE_NOT_FOUND;
        }
        if (ex instanceof HttpRequestMethodNotSupportedException) {
            return ErrorCode.METHOD_NOT_ALLOWED;
        }
        if (ex instanceof HttpMediaTypeNotSupportedException) {
            return ErrorCode.UNSUPPORTED_MEDIA_TYPE;
        }
        return statusCode.is5xxServerError() ? ErrorCode.INTERNAL_ERROR : ErrorCode.INVALID_REQUEST;
    }

    private static ResponseEntity<ApiErrorResponse> toResponse(ErrorCode code) {
        return ResponseEntity.status(code.getStatus()).body(ApiErrorResponse.of(code));
    }
}