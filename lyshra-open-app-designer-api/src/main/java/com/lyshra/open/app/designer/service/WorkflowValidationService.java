package com.lyshra.open.app.designer.service;

import com.lyshra.open.app.designer.domain.WorkflowVersion;
import com.lyshra.open.app.designer.dto.ValidationResult;
import reactor.core.publisher.Mono;

/**
 * Service interface for workflow validation operations.
 * Validates workflow structure and configuration before saving or execution.
 */
public interface WorkflowValidationService {

    /**
     * Validates a workflow version.
     *
     * @param version the workflow version to validate
     * @return Mono containing the validation result
     */
    Mono<ValidationResult> validateWorkflow(WorkflowVersion version);

    /**
     * Validates workflow connections.
     *
     * @param version the workflow version to validate
     * @return Mono containing the validation result
     */
    Mono<ValidationResult> validateConnections(WorkflowVersion version);

    /**
     * Validates step configurations.
     *
     * @param version the workflow version to validate
     * @return Mono containing the validation result
     */
    Mono<ValidationResult> validateStepConfigurations(WorkflowVersion version);

    /**
     * Validates that the workflow has a valid start step.
     *
     * @param version the workflow version to validate
     * @return Mono containing the validation result
     */
    Mono<ValidationResult> validateStartStep(WorkflowVersion version);

    /**
     * Validates error handling configuration.
     *
     * @param version the workflow version to validate
     * @return Mono containing the validation result
     */
    Mono<ValidationResult> validateErrorHandling(WorkflowVersion version);
}
