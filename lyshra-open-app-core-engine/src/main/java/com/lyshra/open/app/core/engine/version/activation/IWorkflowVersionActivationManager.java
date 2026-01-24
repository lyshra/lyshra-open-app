package com.lyshra.open.app.core.engine.version.activation;

import com.lyshra.open.app.integration.contract.version.IVersionedWorkflow;
import com.lyshra.open.app.integration.contract.version.IWorkflowVersion;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Manager for controlling workflow version activation, deactivation, and defaults.
 *
 * <p>This manager enables admins to control which versions are used for new executions,
 * supporting safe rollout and rollback of workflow changes.</p>
 *
 * <p>Key Capabilities:</p>
 * <ul>
 *   <li>Activate/deactivate versions for new executions</li>
 *   <li>Set default version per workflow</li>
 *   <li>Support rollout strategies (canary, gradual, immediate)</li>
 *   <li>Enforce constraints (at least one active version)</li>
 *   <li>Audit logging of all changes</li>
 * </ul>
 *
 * <p>Activation States:</p>
 * <pre>
 * ACTIVE     - Accepts new executions, can be set as default
 * INACTIVE   - Does not accept new executions, existing continue
 * DEPRECATED - Marked for removal, existing executions warned
 * RETIRED    - Fully removed from service
 * </pre>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * // Activate a new version
 * ActivationResult result = manager.activate(
 *     "order-processing",
 *     WorkflowVersion.of(1, 3, 0),
 *     "admin-user",
 *     "New validation rules");
 *
 * // Set as default for new executions
 * manager.setDefaultVersion("order-processing", WorkflowVersion.of(1, 3, 0));
 *
 * // Deactivate old version (existing executions continue)
 * manager.deactivate(
 *     "order-processing",
 *     WorkflowVersion.of(1, 2, 0),
 *     "admin-user",
 *     "Replaced by v1.3.0");
 *
 * // Rollback to previous version
 * manager.rollback("order-processing", WorkflowVersion.of(1, 2, 0), "admin-user");
 * }</pre>
 */
public interface IWorkflowVersionActivationManager {

    /**
     * Activation states for workflow versions.
     */
    enum ActivationState {
        /**
         * Version is active and accepts new executions.
         */
        ACTIVE,

        /**
         * Version is inactive and does not accept new executions.
         * Existing executions continue on this version.
         */
        INACTIVE,

        /**
         * Version is marked for deprecation.
         * May still run but users are warned.
         */
        DEPRECATED,

        /**
         * Version is fully retired and cannot be used.
         */
        RETIRED
    }

    /**
     * Activates a workflow version for new executions.
     *
     * @param workflowId workflow identifier
     * @param version version to activate
     * @param activatedBy user/system performing activation
     * @param reason reason for activation
     * @return activation result
     */
    ActivationResult activate(
            String workflowId,
            IWorkflowVersion version,
            String activatedBy,
            String reason);

    /**
     * Deactivates a workflow version.
     * Existing executions continue, but new ones use other active versions.
     *
     * @param workflowId workflow identifier
     * @param version version to deactivate
     * @param deactivatedBy user/system performing deactivation
     * @param reason reason for deactivation
     * @return activation result
     */
    ActivationResult deactivate(
            String workflowId,
            IWorkflowVersion version,
            String deactivatedBy,
            String reason);

    /**
     * Marks a version as deprecated (warning state).
     *
     * @param workflowId workflow identifier
     * @param version version to deprecate
     * @param deprecatedBy user/system performing deprecation
     * @param reason deprecation reason
     * @param suggestedReplacement suggested replacement version
     * @return activation result
     */
    ActivationResult deprecate(
            String workflowId,
            IWorkflowVersion version,
            String deprecatedBy,
            String reason,
            IWorkflowVersion suggestedReplacement);

    /**
     * Retires a version completely.
     * Fails if there are active executions on this version.
     *
     * @param workflowId workflow identifier
     * @param version version to retire
     * @param retiredBy user/system performing retirement
     * @param reason retirement reason
     * @param force force retirement even with active executions
     * @return activation result
     */
    ActivationResult retire(
            String workflowId,
            IWorkflowVersion version,
            String retiredBy,
            String reason,
            boolean force);

    /**
     * Sets the default version for new executions.
     * The version must be in ACTIVE state.
     *
     * @param workflowId workflow identifier
     * @param version version to set as default
     * @param setBy user/system setting default
     * @param reason reason for change
     * @return result
     */
    ActivationResult setDefaultVersion(
            String workflowId,
            IWorkflowVersion version,
            String setBy,
            String reason);

    /**
     * Gets the current default version for new executions.
     *
     * @param workflowId workflow identifier
     * @return default version if set
     */
    Optional<IVersionedWorkflow> getDefaultVersion(String workflowId);

    /**
     * Rolls back to a previous version by setting it as default.
     *
     * @param workflowId workflow identifier
     * @param targetVersion version to roll back to
     * @param rolledBackBy user/system performing rollback
     * @return result
     */
    ActivationResult rollback(
            String workflowId,
            IWorkflowVersion targetVersion,
            String rolledBackBy);

    /**
     * Gets the activation state of a specific version.
     *
     * @param workflowId workflow identifier
     * @param version version to check
     * @return activation state
     */
    Optional<ActivationState> getActivationState(String workflowId, IWorkflowVersion version);

    /**
     * Gets all active versions for a workflow.
     *
     * @param workflowId workflow identifier
     * @return list of active versions, sorted by version descending
     */
    List<IVersionedWorkflow> getActiveVersions(String workflowId);

    /**
     * Gets all inactive versions for a workflow.
     *
     * @param workflowId workflow identifier
     * @return list of inactive versions
     */
    List<IVersionedWorkflow> getInactiveVersions(String workflowId);

    /**
     * Gets all deprecated versions for a workflow.
     *
     * @param workflowId workflow identifier
     * @return list of deprecated versions
     */
    List<IVersionedWorkflow> getDeprecatedVersions(String workflowId);

    /**
     * Gets all versions with their activation states.
     *
     * @param workflowId workflow identifier
     * @return map of version to state
     */
    Map<IWorkflowVersion, VersionActivationInfo> getAllVersionStates(String workflowId);

    /**
     * Checks if a version can accept new executions.
     *
     * @param workflowId workflow identifier
     * @param version version to check
     * @return true if version accepts new executions
     */
    boolean canAcceptNewExecutions(String workflowId, IWorkflowVersion version);

    /**
     * Gets the activation history for a version.
     *
     * @param workflowId workflow identifier
     * @param version version to check
     * @return list of activation events
     */
    List<ActivationEvent> getActivationHistory(String workflowId, IWorkflowVersion version);

    /**
     * Gets the activation history for a workflow (all versions).
     *
     * @param workflowId workflow identifier
     * @param limit maximum number of events
     * @return list of activation events
     */
    List<ActivationEvent> getWorkflowActivationHistory(String workflowId, int limit);

    /**
     * Result of an activation operation.
     */
    record ActivationResult(
            boolean success,
            String workflowId,
            IWorkflowVersion version,
            ActivationState previousState,
            ActivationState newState,
            String message,
            Instant timestamp
    ) {
        public static ActivationResult success(
                String workflowId,
                IWorkflowVersion version,
                ActivationState previousState,
                ActivationState newState,
                String message) {
            return new ActivationResult(
                    true, workflowId, version, previousState, newState, message, Instant.now());
        }

        public static ActivationResult failure(
                String workflowId,
                IWorkflowVersion version,
                ActivationState currentState,
                String message) {
            return new ActivationResult(
                    false, workflowId, version, currentState, currentState, message, Instant.now());
        }

        public boolean stateChanged() {
            return success && previousState != newState;
        }
    }

    /**
     * Information about a version's activation state.
     */
    record VersionActivationInfo(
            IWorkflowVersion version,
            ActivationState state,
            boolean isDefault,
            Instant stateChangedAt,
            String stateChangedBy,
            Optional<String> stateChangeReason,
            Optional<IWorkflowVersion> suggestedReplacement,
            int activeExecutionCount
    ) {
        public boolean canAcceptNewExecutions() {
            return state == ActivationState.ACTIVE;
        }

        public boolean isDeprecatedOrRetired() {
            return state == ActivationState.DEPRECATED || state == ActivationState.RETIRED;
        }
    }

    /**
     * An activation/deactivation event for audit logging.
     */
    record ActivationEvent(
            String eventId,
            String workflowId,
            IWorkflowVersion version,
            ActivationEventType eventType,
            ActivationState previousState,
            ActivationState newState,
            String performedBy,
            String reason,
            Instant timestamp,
            Map<String, Object> metadata
    ) {
        public enum ActivationEventType {
            ACTIVATED,
            DEACTIVATED,
            DEPRECATED,
            RETIRED,
            DEFAULT_SET,
            DEFAULT_CHANGED,
            ROLLBACK
        }
    }
}
