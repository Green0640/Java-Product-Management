package org.example.productmanagement.dto;

/**
 * 所有 API 錯誤的統一回應格式。
 */
public record ApiErrorResponse(
        ErrorCode code,
        String message
) {
    public static ApiErrorResponse of(ErrorCode code) {
        return new ApiErrorResponse(code, code.getMessage());
    }
}