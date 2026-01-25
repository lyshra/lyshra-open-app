package com.lyshra.open.app.designer.controller;

import com.lyshra.open.app.designer.dto.ValidationResult;
import com.lyshra.open.app.designer.exception.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global exception handler for REST controllers.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResourceNotFoundException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleResourceNotFound(ResourceNotFoundException ex) {
        Map<String, Object> body = createErrorBody(
                HttpStatus.NOT_FOUND.value(),
                ex.getErrorCode(),
                ex.getMessage(),
                Map.of("resourceType", ex.getResourceType(), "resourceId", ex.getResourceId())
        );
        return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).body(body));
    }

    @ExceptionHandler(ValidationException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleValidation(ValidationException ex) {
        ValidationResult result = ex.getValidationResult();
        Map<String, Object> body = createErrorBody(
                HttpStatus.BAD_REQUEST.value(),
                ex.getErrorCode(),
                ex.getMessage(),
                Map.of("errors", result.getErrors(), "warnings", result.getWarnings())
        );
        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body));
    }

    @ExceptionHandler(ConflictException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleConflict(ConflictException ex) {
        Map<String, Object> body = createErrorBody(
                HttpStatus.CONFLICT.value(),
                ex.getErrorCode(),
                ex.getMessage(),
                null
        );
        return Mono.just(ResponseEntity.status(HttpStatus.CONFLICT).body(body));
    }

    @ExceptionHandler(UnauthorizedException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleUnauthorized(UnauthorizedException ex) {
        Map<String, Object> body = createErrorBody(
                HttpStatus.UNAUTHORIZED.value(),
                ex.getErrorCode(),
                ex.getMessage(),
                null
        );
        return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(body));
    }

    @ExceptionHandler(ForbiddenException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleForbidden(ForbiddenException ex) {
        Map<String, Object> body = createErrorBody(
                HttpStatus.FORBIDDEN.value(),
                ex.getErrorCode(),
                ex.getMessage(),
                null
        );
        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).body(body));
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleValidationErrors(WebExchangeBindException ex) {
        List<Map<String, String>> fieldErrors = ex.getFieldErrors().stream()
                .map(error -> Map.of(
                        "field", error.getField(),
                        "message", error.getDefaultMessage() != null ? error.getDefaultMessage() : "Invalid value"
                ))
                .collect(Collectors.toList());

        Map<String, Object> body = createErrorBody(
                HttpStatus.BAD_REQUEST.value(),
                "VALIDATION_ERROR",
                "Request validation failed",
                Map.of("fieldErrors", fieldErrors)
        );
        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body));
    }

    @ExceptionHandler(DesignerException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleDesignerException(DesignerException ex) {
        Map<String, Object> body = createErrorBody(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                ex.getErrorCode(),
                ex.getMessage(),
                null
        );
        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body));
    }

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleGenericException(Exception ex) {
        Map<String, Object> body = createErrorBody(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "INTERNAL_ERROR",
                "An unexpected error occurred",
                null
        );
        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body));
    }

    private Map<String, Object> createErrorBody(
            int status, String code, String message, Map<String, Object> details) {
        Map<String, Object> body = new HashMap<>();
        body.put("timestamp", Instant.now().toString());
        body.put("status", status);
        body.put("code", code);
        body.put("message", message);
        if (details != null && !details.isEmpty()) {
            body.put("details", details);
        }
        return body;
    }
}
