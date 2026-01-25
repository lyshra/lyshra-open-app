package com.lyshra.open.app.designer.schema;

import com.fasterxml.jackson.databind.JsonNode;
import reactor.core.publisher.Mono;

import java.util.Optional;

/**
 * Service interface for validating workflow objects against JSON schemas.
 */
public interface WorkflowSchemaValidator {

    /**
     * Validate a JSON node against a specific schema type.
     *
     * @param jsonNode   The JSON to validate
     * @param schemaType The schema type to validate against
     * @return Validation result
     */
    Mono<SchemaValidationResult> validate(JsonNode jsonNode, SchemaType schemaType);

    /**
     * Validate a JSON string against a specific schema type.
     *
     * @param json       The JSON string to validate
     * @param schemaType The schema type to validate against
     * @return Validation result
     */
    Mono<SchemaValidationResult> validate(String json, SchemaType schemaType);

    /**
     * Validate an object against a specific schema type.
     *
     * @param object     The object to validate (will be converted to JSON)
     * @param schemaType The schema type to validate against
     * @return Validation result
     */
    Mono<SchemaValidationResult> validateObject(Object object, SchemaType schemaType);

    /**
     * Get the schema content for a specific type.
     *
     * @param schemaType The schema type to retrieve
     * @return The schema JSON as a string
     */
    Optional<String> getSchema(SchemaType schemaType);

    /**
     * Get the schema as a JsonNode for a specific type.
     *
     * @param schemaType The schema type to retrieve
     * @return The schema as a JsonNode
     */
    Optional<JsonNode> getSchemaNode(SchemaType schemaType);

    /**
     * Check if a schema is available.
     *
     * @param schemaType The schema type to check
     * @return True if the schema is loaded and available
     */
    boolean isSchemaAvailable(SchemaType schemaType);
}
