package com.lyshra.open.app.designer.exception;

/**
 * Exception thrown when there is a conflict with the current state.
 */
public class ConflictException extends DesignerException {

    public ConflictException(String message) {
        super("CONFLICT", message);
    }

    public ConflictException(String errorCode, String message) {
        super(errorCode, message);
    }
}
