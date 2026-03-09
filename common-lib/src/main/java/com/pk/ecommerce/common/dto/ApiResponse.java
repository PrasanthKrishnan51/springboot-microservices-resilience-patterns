package com.pk.ecommerce.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Uniform envelope for every REST response across the platform.
 *
 * Success:  { success:true,  data:{...},  timestamp:"..." }
 * Error:    { success:false, message:"...", errorCode:"..." }
 * Fallback: { success:false, errorCode:"SERVICE_UNAVAILABLE" }
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private boolean success;
    private String  message;
    private T       data;
    private String  errorCode;
    private String  correlationId;

    @Builder.Default
    private LocalDateTime timestamp = LocalDateTime.now();

    // Factory helpers

    public static <T> ApiResponse<T> ok(T data) {
        return ApiResponse.<T>builder().success(true).data(data).build();
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return ApiResponse.<T>builder().success(true).data(data).message(message).build();
    }

    public static <T> ApiResponse<T> error(String message, String errorCode) {
        return ApiResponse.<T>builder().success(false).message(message).errorCode(errorCode).build();
    }

    /** Used by every Resilience4j fallback method. */
    public static <T> ApiResponse<T> fallback(String reason) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(reason)
                .errorCode("SERVICE_UNAVAILABLE")
                .build();
    }

    public static <T> ApiResponse<T> rateLimited() {
        return ApiResponse.<T>builder()
                .success(false)
                .message("Too many requests. Please slow down.")
                .errorCode("RATE_LIMIT_EXCEEDED")
                .build();
    }
}
