package com.lyshra.open.app.designer.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Controller for serving API documentation resources.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
public class ApiDocsController {

    /**
     * Serve the static OpenAPI specification YAML file.
     */
    @GetMapping(value = "/docs/openapi.yaml", produces = "application/x-yaml")
    public Mono<ResponseEntity<String>> getOpenApiYaml() {
        return Mono.fromCallable(() -> {
            Resource resource = new ClassPathResource("static/api/openapi.yaml");
            if (!resource.exists()) {
                return ResponseEntity.notFound().<String>build();
            }
            String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/x-yaml"))
                    .body(content);
        }).onErrorResume(IOException.class, e -> {
            log.error("Failed to read OpenAPI spec", e);
            return Mono.just(ResponseEntity.internalServerError().build());
        });
    }

    /**
     * Get API version and documentation links.
     */
    @GetMapping(value = "/docs", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<Map<String, Object>>> getApiDocsInfo() {
        Map<String, Object> info = Map.of(
                "apiVersion", "1.0.0",
                "title", "Lyshra OpenApp Workflow Designer API",
                "documentation", Map.of(
                        "swaggerUi", "/swagger-ui.html",
                        "openApiJson", "/v3/api-docs",
                        "openApiYaml", "/api/v1/docs/openapi.yaml"
                ),
                "endpoints", Map.of(
                        "auth", "/api/v1/auth",
                        "workflows", "/api/v1/workflows",
                        "executions", "/api/v1/executions",
                        "processors", "/api/v1/processors",
                        "validation", "/api/v1/validation",
                        "schemas", "/api/v1/schemas"
                )
        );
        return Mono.just(ResponseEntity.ok(info));
    }

    /**
     * Health check endpoint.
     */
    @GetMapping("/health")
    public Mono<ResponseEntity<Map<String, Object>>> healthCheck() {
        Map<String, Object> health = Map.of(
                "status", "UP",
                "timestamp", java.time.Instant.now().toString(),
                "service", "lyshra-openapp-designer-api",
                "version", "1.0.0"
        );
        return Mono.just(ResponseEntity.ok(health));
    }
}
