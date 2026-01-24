package com.lyshra.open.app.core.engine.version;

import com.lyshra.open.app.integration.contract.version.IWorkflowExecutionBinding;
import com.lyshra.open.app.integration.contract.version.IWorkflowVersion;

import java.util.Collection;
import java.util.Optional;

/**
 * Store interface for managing workflow execution bindings.
 * Tracks which version each in-flight execution is bound to.
 *
 * <p>Design Pattern: Repository pattern for execution binding persistence.</p>
 */
public interface IWorkflowExecutionBindingStore {

    /**
     * Saves a new execution binding.
     *
     * @param binding binding to save
     */
    void save(IWorkflowExecutionBinding binding);

    /**
     * Updates an existing execution binding.
     *
     * @param binding updated binding
     */
    void update(IWorkflowExecutionBinding binding);

    /**
     * Gets an execution binding by ID.
     *
     * @param executionId execution ID
     * @return binding if found
     */
    Optional<IWorkflowExecutionBinding> findById(String executionId);

    /**
     * Gets all bindings for a workflow.
     *
     * @param workflowId workflow ID
     * @return all bindings for workflow
     */
    Collection<IWorkflowExecutionBinding> findByWorkflowId(String workflowId);

    /**
     * Gets all bindings for a specific workflow version.
     *
     * @param workflowId workflow ID
     * @param version version
     * @return bindings for version
     */
    Collection<IWorkflowExecutionBinding> findByWorkflowIdAndVersion(String workflowId, IWorkflowVersion version);

    /**
     * Gets all active (non-terminal) bindings for a workflow.
     *
     * @param workflowId workflow ID
     * @return active bindings
     */
    Collection<IWorkflowExecutionBinding> findActiveByWorkflowId(String workflowId);

    /**
     * Gets all active bindings for a specific version.
     *
     * @param workflowId workflow ID
     * @param version version
     * @return active bindings for version
     */
    Collection<IWorkflowExecutionBinding> findActiveByWorkflowIdAndVersion(String workflowId, IWorkflowVersion version);

    /**
     * Gets all bindings in a specific state.
     *
     * @param state execution state
     * @return bindings in state
     */
    Collection<IWorkflowExecutionBinding> findByState(IWorkflowExecutionBinding.ExecutionState state);

    /**
     * Deletes an execution binding.
     *
     * @param executionId execution ID
     * @return true if deleted
     */
    boolean delete(String executionId);

    /**
     * Counts active executions for a version.
     *
     * @param workflowId workflow ID
     * @param version version
     * @return count of active executions
     */
    long countActiveByVersion(String workflowId, IWorkflowVersion version);

    /**
     * Checks if any executions are bound to a version.
     *
     * @param workflowId workflow ID
     * @param version version
     * @return true if any executions bound
     */
    boolean hasExecutionsBoundToVersion(String workflowId, IWorkflowVersion version);
}
