package com.lyshra.open.app.core.engine.version;

import com.lyshra.open.app.integration.contract.version.IVersionedWorkflow;
import com.lyshra.open.app.integration.contract.version.IWorkflowVersion;
import com.lyshra.open.app.integration.contract.version.IWorkflowVersionRegistry;

import java.util.Optional;

/**
 * Service interface extending the registry with additional operations.
 * Provides transactional and lifecycle management capabilities.
 */
public interface IWorkflowVersionRegistryService extends IWorkflowVersionRegistry {

    /**
     * Registers a versioned workflow atomically.
     * Validates compatibility and integration before registration.
     *
     * @param workflow workflow to register
     * @return registration result
     */
    RegistrationResult registerWithValidation(IVersionedWorkflow workflow);

    /**
     * Gets the active (non-deprecated, latest stable) version.
     *
     * @param workflowId workflow ID
     * @return active version
     */
    Optional<IVersionedWorkflow> getActiveVersion(String workflowId);

    /**
     * Sets a specific version as the default for new executions.
     *
     * @param workflowId workflow ID
     * @param version version to set as default
     * @return true if successful
     */
    boolean setDefaultVersion(String workflowId, IWorkflowVersion version);

    /**
     * Gets the default version for new executions.
     *
     * @param workflowId workflow ID
     * @return default version
     */
    Optional<IVersionedWorkflow> getDefaultVersion(String workflowId);

    /**
     * Checks if any in-flight executions are bound to the given version.
     *
     * @param workflowId workflow ID
     * @param version version to check
     * @return true if executions exist
     */
    boolean hasActiveExecutions(String workflowId, IWorkflowVersion version);

    /**
     * Registration result with validation status.
     */
    record RegistrationResult(
            boolean success,
            String message,
            Optional<IVersionedWorkflow> registeredWorkflow
    ) {
        public static RegistrationResult success(IVersionedWorkflow workflow) {
            return new RegistrationResult(true, "Registration successful", Optional.of(workflow));
        }

        public static RegistrationResult failure(String reason) {
            return new RegistrationResult(false, reason, Optional.empty());
        }
    }
}
