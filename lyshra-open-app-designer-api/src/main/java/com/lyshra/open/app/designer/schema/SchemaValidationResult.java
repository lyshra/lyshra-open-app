package com.lyshra.open.app.designer.schema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Result of JSON schema validation.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SchemaValidationResult {

    /**
     * Whether the validation passed
     */
    private boolean valid;

    /**
     * Schema type that was used for validation
     */
    private SchemaType schemaType;

    /**
     * List of validation errors (empty if valid)
     */
    @Builder.Default
    private List<SchemaValidationError> errors = new ArrayList<>();

    /**
     * Number of errors found
     */
    public int getErrorCount() {
        return Optional.ofNullable(errors).map(List::size).orElse(0);
    }

    /**
     * Create a successful validation result.
     */
    public static SchemaValidationResult success(SchemaType schemaType) {
        return SchemaValidationResult.builder()
                .valid(true)
                .schemaType(schemaType)
                .errors(List.of())
                .build();
    }

    /**
     * Create a failed validation result.
     */
    public static SchemaValidationResult failure(SchemaType schemaType, List<SchemaValidationError> errors) {
        return SchemaValidationResult.builder()
                .valid(false)
                .schemaType(schemaType)
                .errors(errors)
                .build();
    }

    /**
     * Get a summary of validation errors.
     */
    public String getErrorSummary() {
        if (valid || errors == null || errors.isEmpty()) {
            return "No errors";
        }

        StringBuilder sb = new StringBuilder();
        sb.append(errors.size()).append(" validation error(s):\n");
        for (int i = 0; i < Math.min(errors.size(), 5); i++) {
            SchemaValidationError error = errors.get(i);
            sb.append("  - ").append(error.getPath()).append(": ").append(error.getMessage()).append("\n");
        }
        if (errors.size() > 5) {
            sb.append("  ... and ").append(errors.size() - 5).append(" more errors");
        }
        return sb.toString();
    }
}
