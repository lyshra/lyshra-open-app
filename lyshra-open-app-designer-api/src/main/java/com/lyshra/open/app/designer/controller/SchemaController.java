package com.lyshra.open.app.designer.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.lyshra.open.app.designer.schema.SchemaType;
import com.lyshra.open.app.designer.schema.SchemaValidationResult;
import com.lyshra.open.app.designer.schema.WorkflowSchemaValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST controller for accessing JSON schemas and performing validation.
 * This endpoint allows frontend to fetch schemas for client-side validation
 * and UI generation.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/schemas")
@RequiredArgsConstructor
public class SchemaController {

    private final WorkflowSchemaValidator schemaValidator;

    /**
     * Get list of all available schemas.
     */
    @GetMapping
    public Mono<ResponseEntity<List<Map<String, Object>>>> listSchemas() {
        List<Map<String, Object>> schemas = Arrays.stream(SchemaType.values())
                .map(type -> {
                    Map<String, Object> schema = new HashMap<>();
                    schema.put("type", type.name());
                    schema.put("fileName", type.getFileName());
                    schema.put("available", schemaValidator.isSchemaAvailable(type));
                    return schema;
                })
                .collect(Collectors.toList());

        return Mono.just(ResponseEntity.ok(schemas));
    }

    /**
     * Get a specific schema by type.
     */
    @GetMapping(value = "/{schemaType}", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<String>> getSchema(@PathVariable String schemaType) {
        SchemaType type;
        try {
            type = SchemaType.valueOf(schemaType.toUpperCase().replace("-", "_"));
        } catch (IllegalArgumentException e) {
            return Mono.just(ResponseEntity.notFound().build());
        }

        return Mono.justOrEmpty(schemaValidator.getSchema(type))
                .map(schema -> ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(schema))
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * Get a specific schema as a JSON node for easier processing.
     */
    @GetMapping(value = "/{schemaType}/node", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<ResponseEntity<JsonNode>> getSchemaNode(@PathVariable String schemaType) {
        SchemaType type;
        try {
            type = SchemaType.valueOf(schemaType.toUpperCase().replace("-", "_"));
        } catch (IllegalArgumentException e) {
            return Mono.just(ResponseEntity.notFound().build());
        }

        return Mono.justOrEmpty(schemaValidator.getSchemaNode(type))
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * Validate JSON against a specific schema.
     */
    @PostMapping("/{schemaType}/validate")
    public Mono<ResponseEntity<SchemaValidationResult>> validateAgainstSchema(
            @PathVariable String schemaType,
            @RequestBody JsonNode jsonNode) {

        SchemaType type;
        try {
            type = SchemaType.valueOf(schemaType.toUpperCase().replace("-", "_"));
        } catch (IllegalArgumentException e) {
            return Mono.just(ResponseEntity.badRequest().build());
        }

        return schemaValidator.validate(jsonNode, type)
                .map(result -> {
                    if (result.isValid()) {
                        return ResponseEntity.ok(result);
                    } else {
                        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(result);
                    }
                });
    }

    /**
     * Get all schemas bundled in a single response for frontend caching.
     */
    @GetMapping("/bundle")
    public Mono<ResponseEntity<Map<String, JsonNode>>> getAllSchemas() {
        Map<String, JsonNode> schemas = new HashMap<>();

        for (SchemaType type : SchemaType.values()) {
            schemaValidator.getSchemaNode(type)
                    .ifPresent(node -> schemas.put(type.name(), node));
        }

        return Mono.just(ResponseEntity.ok(schemas));
    }
}
