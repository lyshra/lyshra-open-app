package com.lyshra.open.app.designer.schema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a single validation error from JSON schema validation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SchemaValidationError {

    /**
     * JSON path to the invalid field (e.g., "$.steps[0].name")
     */
    private String path;

    /**
     * Human-readable error message
     */
    private String message;

    /**
     * Schema keyword that triggered the error (e.g., "required", "type", "minLength")
     */
    private String keyword;

    /**
     * Expected value or constraint
     */
    private String expected;

    /**
     * Actual value that failed validation
     */
    private Object actual;

    /**
     * Schema ID where the error occurred
     */
    private String schemaPath;
}
