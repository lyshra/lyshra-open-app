package com.lyshra.open.app.designer.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * DTO containing workflow validation results.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationResult {

    private boolean valid;
    @Builder.Default
    private List<ValidationError> errors = new ArrayList<>();
    @Builder.Default
    private List<ValidationWarning> warnings = new ArrayList<>();

    /**
     * Creates a valid result with no errors.
     *
     * @return a valid validation result
     */
    public static ValidationResult valid() {
        return ValidationResult.builder()
                .valid(true)
                .errors(new ArrayList<>())
                .warnings(new ArrayList<>())
                .build();
    }

    /**
     * Creates an invalid result with errors.
     *
     * @param errors the validation errors
     * @return an invalid validation result
     */
    public static ValidationResult invalid(List<ValidationError> errors) {
        return ValidationResult.builder()
                .valid(false)
                .errors(errors != null ? errors : new ArrayList<>())
                .warnings(new ArrayList<>())
                .build();
    }

    /**
     * Adds an error to the result.
     *
     * @param error the error to add
     */
    public void addError(ValidationError error) {
        if (errors == null) {
            errors = new ArrayList<>();
        }
        errors.add(error);
        valid = false;
    }

    /**
     * Adds a warning to the result.
     *
     * @param warning the warning to add
     */
    public void addWarning(ValidationWarning warning) {
        if (warnings == null) {
            warnings = new ArrayList<>();
        }
        warnings.add(warning);
    }

    /**
     * Merges another validation result into this one.
     *
     * @param other the other validation result
     * @return this validation result
     */
    public ValidationResult merge(ValidationResult other) {
        if (other == null) {
            return this;
        }
        if (other.getErrors() != null) {
            if (errors == null) {
                errors = new ArrayList<>();
            }
            errors.addAll(other.getErrors());
        }
        if (other.getWarnings() != null) {
            if (warnings == null) {
                warnings = new ArrayList<>();
            }
            warnings.addAll(other.getWarnings());
        }
        valid = valid && other.isValid();
        return this;
    }
}
