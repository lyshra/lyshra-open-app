package com.lyshra.open.app.designer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a validation warning.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationWarning {

    private String code;
    private String message;
    private String field;
    private String stepId;
    private String stepName;

    /**
     * Creates a validation warning for a specific field.
     *
     * @param code the warning code
     * @param message the warning message
     * @param field the field name
     * @return the validation warning
     */
    public static ValidationWarning fieldWarning(String code, String message, String field) {
        return ValidationWarning.builder()
                .code(code)
                .message(message)
                .field(field)
                .build();
    }

    /**
     * Creates a validation warning for a specific step.
     *
     * @param code the warning code
     * @param message the warning message
     * @param stepId the step ID
     * @param stepName the step name
     * @return the validation warning
     */
    public static ValidationWarning stepWarning(String code, String message, String stepId, String stepName) {
        return ValidationWarning.builder()
                .code(code)
                .message(message)
                .stepId(stepId)
                .stepName(stepName)
                .build();
    }
}
