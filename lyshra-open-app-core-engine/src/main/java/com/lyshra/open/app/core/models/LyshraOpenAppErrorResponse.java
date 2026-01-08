package com.lyshra.open.app.core.models;

import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorIdentifier;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowStepIdentifier;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppHttpStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

@Data
@Builder
public class LyshraOpenAppErrorResponse {
    private final String requestId;
    private final Instant timestamp;
    private final ILyshraOpenAppWorkflowStepIdentifier workflowStepIdentifier;
    private final ILyshraOpenAppProcessorIdentifier processorIdentifier;
    private final String errorCode;
    private final LyshraOpenAppHttpStatus httpStatus;
    private final String errorMessage;
    private final String resolutionMessage;
    private final String rootCauseMessage;
    private final String stackTrace;
    private final Object additionalInfo;
}
