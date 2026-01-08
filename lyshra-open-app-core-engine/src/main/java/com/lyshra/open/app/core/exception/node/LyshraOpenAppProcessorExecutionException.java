package com.lyshra.open.app.core.exception.node;

import com.lyshra.open.app.integration.contract.ILyshraOpenAppErrorInfo;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorIdentifier;
import com.lyshra.open.app.integration.exception.ILyshraOpenAppProcessorException;
import lombok.Getter;
import lombok.ToString;

import java.util.Map;
import java.util.Optional;

@Getter
@ToString
public class LyshraOpenAppProcessorExecutionException extends RuntimeException {
    private static final String ERROR_MESSAGE_TEMPLATE = "Processor Execution Failed. " +
            "ProcessorIdentifier: [%s], " +
            "ErrorInfo: [%s], " +
            "TemplateVariables: [%s], " +
            "RootCause: [%s], " +
            "InnerCause: [%s], " +
            "AdditionalInfo: [%s]";

    private final ILyshraOpenAppProcessorIdentifier processorIdentifier;
    private final ILyshraOpenAppErrorInfo errorInfo;
    private final Map<String, String> templateVariables;
    private final Throwable rootCause;
    private final Object additionalInfo;

    public LyshraOpenAppProcessorExecutionException(
            ILyshraOpenAppProcessorIdentifier processorIdentifier,
            ILyshraOpenAppErrorInfo errorInfo) {
        this(processorIdentifier, errorInfo, Map.of(), null, null);
    }

    public LyshraOpenAppProcessorExecutionException(
            ILyshraOpenAppProcessorIdentifier processorIdentifier,
            ILyshraOpenAppErrorInfo errorInfo,
            Map<String, String> templateVariables,
            Object additionalInfo) {
        this(processorIdentifier, errorInfo, templateVariables, null, additionalInfo);
    }

    public LyshraOpenAppProcessorExecutionException(
            ILyshraOpenAppProcessorIdentifier processorIdentifier,
            ILyshraOpenAppErrorInfo errorInfo,
            Object additionalInfo) {
        this(processorIdentifier, errorInfo, Map.of(), null, additionalInfo);
    }

    public LyshraOpenAppProcessorExecutionException(
            ILyshraOpenAppProcessorIdentifier processorIdentifier,
            ILyshraOpenAppErrorInfo errorInfo,
            Throwable rootCause) {
        this(processorIdentifier, errorInfo, Map.of(), rootCause, null);
    }

    public LyshraOpenAppProcessorExecutionException(
            ILyshraOpenAppProcessorIdentifier processorIdentifier,
            ILyshraOpenAppProcessorException lyshraOpenAppProcessorException) {

        this(
                processorIdentifier,
                lyshraOpenAppProcessorException.getErrorInfo(),
                lyshraOpenAppProcessorException.getTemplateVariables(),
                lyshraOpenAppProcessorException.getRootCause(),
                lyshraOpenAppProcessorException.getAdditionalInfo()
        );
    }

    public LyshraOpenAppProcessorExecutionException(
            ILyshraOpenAppProcessorIdentifier processorIdentifier,
            ILyshraOpenAppErrorInfo errorInfo,
            Map<String, String> templateVariables,
            Throwable rootCause,
            Object additionalInfo) {

        super(
                String.format(
                        ERROR_MESSAGE_TEMPLATE,
                        processorIdentifier,
                        errorInfo,
                        templateVariables,
                        Optional.ofNullable(rootCause).map(Throwable::getMessage).orElse(null),
                        Optional.ofNullable(rootCause).map(Throwable::getCause).map(Throwable::getMessage).orElse(null),
                        additionalInfo
                ),
                rootCause
        );
        this.processorIdentifier = processorIdentifier;
        this.errorInfo = errorInfo;
        this.templateVariables = templateVariables;
        this.rootCause = rootCause;
        this.additionalInfo = additionalInfo;
    }

}
