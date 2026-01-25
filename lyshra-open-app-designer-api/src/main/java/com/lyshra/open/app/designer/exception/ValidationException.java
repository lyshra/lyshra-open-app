package com.lyshra.open.app.designer.exception;

import com.lyshra.open.app.designer.dto.ValidationResult;

/**
 * Exception thrown when workflow validation fails.
 */
public class ValidationException extends DesignerException {

    private final ValidationResult validationResult;

    public ValidationException(ValidationResult validationResult) {
        super("VALIDATION_FAILED", "Workflow validation failed");
        this.validationResult = validationResult;
    }

    public ValidationException(String message, ValidationResult validationResult) {
        super("VALIDATION_FAILED", message);
        this.validationResult = validationResult;
    }

    public ValidationResult getValidationResult() {
        return validationResult;
    }
}
