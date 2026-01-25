package com.lyshra.open.app.designer.schema.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lyshra.open.app.designer.schema.SchemaType;
import com.lyshra.open.app.designer.schema.SchemaValidationError;
import com.lyshra.open.app.designer.schema.SchemaValidationResult;
import com.lyshra.open.app.designer.schema.WorkflowSchemaValidator;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementation of WorkflowSchemaValidator using networknt json-schema-validator.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowSchemaValidatorImpl implements WorkflowSchemaValidator {

    private final ObjectMapper objectMapper;

    private final Map<SchemaType, JsonSchema> schemaCache = new EnumMap<>(SchemaType.class);
    private final Map<SchemaType, String> schemaContentCache = new EnumMap<>(SchemaType.class);
    private final Map<SchemaType, JsonNode> schemaNodeCache = new EnumMap<>(SchemaType.class);

    private JsonSchemaFactory schemaFactory;

    @PostConstruct
    public void init() {
        SchemaValidatorsConfig config = SchemaValidatorsConfig.builder()
                .build();

        schemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);

        for (SchemaType schemaType : SchemaType.values()) {
            loadSchema(schemaType);
        }

        log.info("Loaded {} workflow schemas", schemaCache.size());
    }

    private void loadSchema(SchemaType schemaType) {
        try {
            ClassPathResource resource = new ClassPathResource(schemaType.getResourcePath());

            if (!resource.exists()) {
                log.warn("Schema file not found: {}", schemaType.getResourcePath());
                return;
            }

            try (InputStream is = resource.getInputStream()) {
                String content = new String(is.readAllBytes(), StandardCharsets.UTF_8);
                schemaContentCache.put(schemaType, content);

                JsonNode schemaNode = objectMapper.readTree(content);
                schemaNodeCache.put(schemaType, schemaNode);

                JsonSchema schema = schemaFactory.getSchema(schemaNode);
                schemaCache.put(schemaType, schema);

                log.debug("Loaded schema: {}", schemaType.getFileName());
            }
        } catch (IOException e) {
            log.error("Failed to load schema: {}", schemaType.getFileName(), e);
        }
    }

    @Override
    public Mono<SchemaValidationResult> validate(JsonNode jsonNode, SchemaType schemaType) {
        return Mono.fromCallable(() -> {
            JsonSchema schema = schemaCache.get(schemaType);

            if (schema == null) {
                return SchemaValidationResult.failure(schemaType, List.of(
                        SchemaValidationError.builder()
                                .path("$")
                                .message("Schema not available: " + schemaType.getFileName())
                                .keyword("schema")
                                .build()
                ));
            }

            Set<ValidationMessage> validationMessages = schema.validate(jsonNode);

            if (validationMessages.isEmpty()) {
                return SchemaValidationResult.success(schemaType);
            }

            List<SchemaValidationError> errors = validationMessages.stream()
                    .map(this::toSchemaValidationError)
                    .collect(Collectors.toList());

            return SchemaValidationResult.failure(schemaType, errors);
        });
    }

    @Override
    public Mono<SchemaValidationResult> validate(String json, SchemaType schemaType) {
        return Mono.fromCallable(() -> {
            try {
                JsonNode jsonNode = objectMapper.readTree(json);
                return validate(jsonNode, schemaType).block();
            } catch (JsonProcessingException e) {
                return SchemaValidationResult.failure(schemaType, List.of(
                        SchemaValidationError.builder()
                                .path("$")
                                .message("Invalid JSON: " + e.getMessage())
                                .keyword("format")
                                .build()
                ));
            }
        });
    }

    @Override
    public Mono<SchemaValidationResult> validateObject(Object object, SchemaType schemaType) {
        return Mono.fromCallable(() -> {
            JsonNode jsonNode = objectMapper.valueToTree(object);
            return validate(jsonNode, schemaType).block();
        });
    }

    @Override
    public Optional<String> getSchema(SchemaType schemaType) {
        return Optional.ofNullable(schemaContentCache.get(schemaType));
    }

    @Override
    public Optional<JsonNode> getSchemaNode(SchemaType schemaType) {
        return Optional.ofNullable(schemaNodeCache.get(schemaType));
    }

    @Override
    public boolean isSchemaAvailable(SchemaType schemaType) {
        return schemaCache.containsKey(schemaType);
    }

    private SchemaValidationError toSchemaValidationError(ValidationMessage message) {
        return SchemaValidationError.builder()
                .path(message.getInstanceLocation().toString())
                .message(message.getMessage())
                .keyword(message.getType())
                .schemaPath(message.getSchemaLocation().toString())
                .build();
    }
}
