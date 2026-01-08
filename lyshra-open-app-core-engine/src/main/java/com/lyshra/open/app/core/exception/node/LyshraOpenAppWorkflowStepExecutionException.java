package com.lyshra.open.app.core.exception.node;

import com.lyshra.open.app.integration.contract.ILyshraOpenAppErrorInfo;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorIdentifier;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowStepIdentifier;
import lombok.Getter;
import lombok.ToString;

import java.util.Map;
import java.util.Optional;

@Getter
@ToString
public class LyshraOpenAppWorkflowStepExecutionException extends RuntimeException {
    private static final String ERROR_MESSAGE_TEMPLATE = "Workflow Step Execution Failed. " +
            "WorkflowStepIdentifier: [%s], " +
            "ProcessorIdentifier: [%s], " +
            "ErrorInfo: [%s], " +
            "TemplateVariables: [%s], " +
            "RootCause: [%s], " +
            "InnerCause: [%s], " +
            "AdditionalInfo: [%s]";

    private final ILyshraOpenAppWorkflowStepIdentifier workflowStepIdentifier;
    private final ILyshraOpenAppProcessorIdentifier processorIdentifier;
    private final ILyshraOpenAppErrorInfo errorInfo;
    private final Map<String, String> templateVariables;
    private final Throwable rootCause;
    private final Object additionalInfo;

    public LyshraOpenAppWorkflowStepExecutionException(
            ILyshraOpenAppWorkflowStepIdentifier workflowStepIdentifier,
            LyshraOpenAppProcessorExecutionException processorExecutionException) {

        super(
                String.format(
                        ERROR_MESSAGE_TEMPLATE,
                        workflowStepIdentifier,
                        processorExecutionException.getProcessorIdentifier(),
                        processorExecutionException.getErrorInfo(),
                        processorExecutionException.getTemplateVariables(),
                        Optional.ofNullable(processorExecutionException.getRootCause())
                                .map(Throwable::getMessage)
                                .orElse(null),
                        Optional.ofNullable(processorExecutionException.getRootCause())
                                .map(Throwable::getCause)
                                .map(Throwable::getMessage)
                                .orElse(null),
                        processorExecutionException.getAdditionalInfo()
                ),
                processorExecutionException.getRootCause()
        );

        this.workflowStepIdentifier = workflowStepIdentifier;
        this.processorIdentifier = processorExecutionException.getProcessorIdentifier();
        this.errorInfo = processorExecutionException.getErrorInfo();
        this.templateVariables = processorExecutionException.getTemplateVariables();
        this.rootCause = processorExecutionException.getRootCause();
        this.additionalInfo = processorExecutionException.getAdditionalInfo();
    }

}
