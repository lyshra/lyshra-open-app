package com.lyshra.open.app.integration.contract;

import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorIdentifier;
import com.lyshra.open.app.integration.contract.processor.ILyshraOpenAppProcessorResult;
import com.lyshra.open.app.integration.contract.version.IWorkflowInstanceMetadata;
import com.lyshra.open.app.integration.contract.version.IWorkflowVersion;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowIdentifier;
import com.lyshra.open.app.integration.contract.workflow.ILyshraOpenAppWorkflowStepIdentifier;

import java.util.Map;
import java.util.Optional;

/**
 * Context for workflow execution that carries data, variables, and version metadata.
 *
 * <p>The context serves as the state carrier throughout workflow execution:</p>
 * <ul>
 *   <li>Data - the main payload being processed</li>
 *   <li>Variables - workflow-scoped variables for inter-step communication</li>
 *   <li>Instance Metadata - version and execution tracking information</li>
 *   <li>History - execution audit trail</li>
 * </ul>
 *
 * <p>Version Tracking:</p>
 * <p>Each context carries instance metadata that tracks which workflow version
 * it started with. This ensures that even after workflow updates, the execution
 * continues using the correct definition.</p>
 */
public interface ILyshraOpenAppContext {

    // data helper methods
    Object getData();
    void setData(Object data);

    // variables helper methods
    Map<String, Object> getVariables();
    void setVariables(Map<String, Object> variables);
    void addVariable(String key, Object value);
    void updateVariable(String key, Object value);
    void removeVariable(String key);
    Object getVariable(String key);
    boolean hasVariable(String key);
    void clearVariables();

    void captureWorkflowStart(ILyshraOpenAppWorkflowIdentifier identifier);
    void captureWorkflowEnd(ILyshraOpenAppWorkflowIdentifier identifier);
    void captureWorkflowEnd(ILyshraOpenAppWorkflowIdentifier identifier, Throwable throwable);

    void captureWorkflowStepStart(ILyshraOpenAppWorkflowStepIdentifier identifier);
    void captureWorkflowStepEnd(ILyshraOpenAppWorkflowStepIdentifier identifier, Throwable throwable);
    void captureWorkflowStepEnd(ILyshraOpenAppWorkflowStepIdentifier identifier, String next);

    void captureProcessorStart(ILyshraOpenAppProcessorIdentifier identifier, Map<String, Object> rawInput);
    void captureProcessorEnd(ILyshraOpenAppProcessorIdentifier identifier, ILyshraOpenAppProcessorResult res);
    void captureProcessorEnd(ILyshraOpenAppProcessorIdentifier identifier, Throwable throwable);

    // ==================== Version Tracking Methods ====================

    /**
     * Returns the instance metadata for this workflow execution.
     * Contains version information including which version was used at start.
     *
     * @return instance metadata, empty if not set
     */
    Optional<IWorkflowInstanceMetadata> getInstanceMetadata();

    /**
     * Sets the instance metadata for this workflow execution.
     *
     * @param metadata instance metadata with version info
     */
    void setInstanceMetadata(IWorkflowInstanceMetadata metadata);

    /**
     * Returns the workflow version this execution started with.
     * Shortcut for getInstanceMetadata().map(m -> m.getOriginalVersion()).
     *
     * @return original workflow version, empty if not set
     */
    default Optional<IWorkflowVersion> getWorkflowVersion() {
        return getInstanceMetadata().map(IWorkflowInstanceMetadata::getOriginalVersion);
    }

    /**
     * Returns the current workflow version being executed.
     * May differ from original if migrated.
     *
     * @return current workflow version, empty if not set
     */
    default Optional<IWorkflowVersion> getCurrentWorkflowVersion() {
        return getInstanceMetadata().map(IWorkflowInstanceMetadata::getCurrentVersion);
    }

    /**
     * Returns the workflow version string this execution started with.
     *
     * @return version string (e.g., "1.2.0"), empty if not set
     */
    default Optional<String> getWorkflowVersionString() {
        return getWorkflowVersion().map(IWorkflowVersion::toVersionString);
    }

    /**
     * Returns the instance ID for this workflow execution.
     *
     * @return instance ID, empty if not set
     */
    default Optional<String> getInstanceId() {
        return getInstanceMetadata().map(IWorkflowInstanceMetadata::getInstanceId);
    }

    /**
     * Returns the correlation ID for distributed tracing.
     *
     * @return correlation ID, empty if not set
     */
    default Optional<String> getCorrelationId() {
        return getInstanceMetadata().flatMap(IWorkflowInstanceMetadata::getCorrelationId);
    }

    /**
     * Checks if this execution has been migrated from its original version.
     *
     * @return true if migrated
     */
    default boolean isMigrated() {
        return getInstanceMetadata().map(IWorkflowInstanceMetadata::isMigrated).orElse(false);
    }

    /**
     * Returns the schema hash of the workflow definition at start.
     *
     * @return schema hash, empty if not set
     */
    default Optional<String> getSchemaHash() {
        return getInstanceMetadata().map(IWorkflowInstanceMetadata::getSchemaHash);
    }
}
