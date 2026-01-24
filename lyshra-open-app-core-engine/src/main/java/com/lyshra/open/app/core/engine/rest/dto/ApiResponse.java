package com.lyshra.open.app.core.engine.rest.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Standard API response wrapper for consistent response format.
 *
 * @param <T> the type of data in the response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResponse<T> {

    /**
     * Whether the request was successful.
     */
    private boolean success;

    /**
     * The response data (null if error).
     */
    private T data;

    /**
     * Error message (null if success).
     */
    private String error;

    /**
     * Error code for programmatic handling.
     */
    private String errorCode;

    /**
     * Response timestamp.
     */
    @Builder.Default
    private Instant timestamp = Instant.now();

    /**
     * Creates a successful response with data.
     */
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Creates a successful response without data.
     */
    public static <T> ApiResponse<T> success() {
        return ApiResponse.<T>builder()
                .success(true)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Creates an error response.
     */
    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .error(message)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * Creates an error response with error code.
     */
    public static <T> ApiResponse<T> error(String message, String errorCode) {
        return ApiResponse.<T>builder()
                .success(false)
                .error(message)
                .errorCode(errorCode)
                .timestamp(Instant.now())
                .build();
    }
}
