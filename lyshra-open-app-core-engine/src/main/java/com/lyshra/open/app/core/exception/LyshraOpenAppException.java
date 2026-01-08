package com.lyshra.open.app.core.exception;

public class LyshraOpenAppException extends Exception {
    public LyshraOpenAppException(String message) {
        super(message);
    }
    public LyshraOpenAppException(String message, Throwable cause) {
        super(message, cause);
    }
    public LyshraOpenAppException(Throwable cause) {
        super(cause);
    }
}
