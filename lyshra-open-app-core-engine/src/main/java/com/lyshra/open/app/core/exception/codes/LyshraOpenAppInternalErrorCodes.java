package com.lyshra.open.app.core.exception.codes;

import com.lyshra.open.app.integration.contract.ILyshraOpenAppErrorInfo;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppHttpStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public enum LyshraOpenAppInternalErrorCodes implements ILyshraOpenAppErrorInfo {

    PROCESSOR_INPUT_TRANSFORMATION_FAILED(
            "LYSHRA_ERR_0001",
            LyshraOpenAppHttpStatus.BAD_REQUEST,
            "processor.input.transformation.failed",
            "processor.input.transformation.failed.resolution"
    ),

    PROCESSOR_INPUT_CONSTRAINT_VIOLATION_FAILED(
            "LYSHRA_ERR_0002",
            LyshraOpenAppHttpStatus.BAD_REQUEST,
            "processor.input.constraint.violation.failed",
            "processor.input.constraint.violation.failed.resolution"
    ),

    PROCESSOR_INPUT_VALIDATION_PLUGIN_INTERNAL_ERROR(
            "LYSHRA_ERR_0003",
            LyshraOpenAppHttpStatus.INTERNAL_SERVER_ERROR,
            "processor.input.validation.plugin.internal.error",
            "processor.input.validation.plugin.internal.error.resolution"
    ),

    PROCESSOR_INPUT_CONSTRAINT_VIOLATION_EXECUTION_FAILED(
            "LYSHRA_ERR_0004",
            LyshraOpenAppHttpStatus.INTERNAL_SERVER_ERROR,
            "processor.input.constraint.violation.execution.failed",
            "processor.input.constraint.violation.execution.failed.resolution"
    ),

    PROCESSOR_EXECUTION_FAILED(
            "LYSHRA_ERR_0005",
            LyshraOpenAppHttpStatus.INTERNAL_SERVER_ERROR,
            "processor.execution.failed",
            "processor.execution.failed.resolution"
    )

    ;

    private final String errorCode;
    private final LyshraOpenAppHttpStatus httpStatus;
    private final String errorTemplate;
    private final String resolutionTemplate;
}
