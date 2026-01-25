package com.lyshra.open.app.designer.exception;

/**
 * Base exception for workflow designer errors.
 */
public class DesignerException extends RuntimeException {

    private final String errorCode;

    public DesignerException(String message) {
        super(message);
        this.errorCode = "DESIGNER_ERROR";
    }

    public DesignerException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public DesignerException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
