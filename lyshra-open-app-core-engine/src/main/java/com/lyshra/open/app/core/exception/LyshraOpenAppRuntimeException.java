package com.lyshra.open.app.core.exception;

public class LyshraOpenAppRuntimeException extends RuntimeException {
    public LyshraOpenAppRuntimeException(String message) {
        super(message);
    }
    public LyshraOpenAppRuntimeException(String message, Throwable cause) {
        super(message, cause);
    }
    public LyshraOpenAppRuntimeException(Throwable cause) {
        super(cause);
    }
}
