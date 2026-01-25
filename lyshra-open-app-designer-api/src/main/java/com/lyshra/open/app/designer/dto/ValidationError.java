package com.lyshra.open.app.designer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a validation error.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationError {

    private String code;
    private String message;
    private String field;
    private String stepId;
    private String stepName;

    /**
     * Creates a validation error for a specific field.
     *
     * @param code the error code
     * @param message the error message
     * @param field the field name
     * @return the validation error
     */
    public static ValidationError fieldError(String code, String message, String field) {
        return ValidationError.builder()
                .code(code)
                .message(message)
                .field(field)
                .build();
    }

    /**
     * Creates a validation error for a specific step.
     *
     * @param code the error code
     * @param message the error message
     * @param stepId the step ID
     * @param stepName the step name
     * @return the validation error
     */
    public static ValidationError stepError(String code, String message, String stepId, String stepName) {
        return ValidationError.builder()
                .code(code)
                .message(message)
                .stepId(stepId)
                .stepName(stepName)
                .build();
    }
}
