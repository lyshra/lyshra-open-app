package com.lyshra.open.app.integration.exception;

import com.lyshra.open.app.integration.contract.ILyshraOpenAppErrorInfo;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.ToString;

import java.util.Map;

@Getter
@ToString
@AllArgsConstructor
public class LyshraOpenAppProcessorRuntimeException extends RuntimeException implements ILyshraOpenAppProcessorException {
    protected final ILyshraOpenAppErrorInfo errorInfo;
    protected final Map<String, String> templateVariables;
    protected final Throwable rootCause;
    protected final Object additionalInfo;

    public LyshraOpenAppProcessorRuntimeException(ILyshraOpenAppErrorInfo errorInfo) {
        this(errorInfo, Map.of(), null, null);
    }

    public LyshraOpenAppProcessorRuntimeException(ILyshraOpenAppErrorInfo errorInfo, Map<String, String> templateVariables) {
        this(errorInfo, templateVariables, null, null);
    }

    public LyshraOpenAppProcessorRuntimeException(ILyshraOpenAppErrorInfo errorInfo, Throwable rootCause) {
        this(errorInfo, Map.of(), rootCause, null);
    }

}
