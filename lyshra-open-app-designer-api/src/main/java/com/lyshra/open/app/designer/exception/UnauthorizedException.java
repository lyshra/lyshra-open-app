package com.lyshra.open.app.designer.exception;

/**
 * Exception thrown when authentication fails.
 */
public class UnauthorizedException extends DesignerException {

    public UnauthorizedException(String message) {
        super("UNAUTHORIZED", message);
    }
}
