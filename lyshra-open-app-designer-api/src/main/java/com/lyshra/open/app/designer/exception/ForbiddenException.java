package com.lyshra.open.app.designer.exception;

/**
 * Exception thrown when access is forbidden.
 */
public class ForbiddenException extends DesignerException {

    public ForbiddenException(String message) {
        super("FORBIDDEN", message);
    }
}
