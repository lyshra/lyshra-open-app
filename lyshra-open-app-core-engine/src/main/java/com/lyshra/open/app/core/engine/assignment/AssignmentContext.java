package com.lyshra.open.app.core.engine.assignment;

import com.lyshra.open.app.integration.contract.ILyshraOpenAppContext;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppHumanTaskType;
import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Context information provided to assignment strategies for making assignment decisions.
 *
 * <p>This class encapsulates all relevant information about the task, workflow,
 * and configuration that a strategy might need to determine the appropriate assignees.</p>
 *
 * <h2>Context Information</h2>
 * <ul>
 *   <li><b>Task Info</b> - Task type, title, priority, data</li>
 *   <li><b>Workflow Info</b> - Workflow/step IDs, current context</li>
 *   <li><b>Configuration</b> - Strategy-specific settings</li>
 *   <li><b>Candidates</b> - Pre-defined candidate users/groups</li>
 *   <li><b>Metadata</b> - Additional context-specific data</li>
 * </ul>
 */
@Data
@Builder
public class AssignmentContext {

    // ========================================================================
    // TASK INFORMATION
    // ========================================================================

    /**
     * The type of human task being assigned.
     */
    private final LyshraOpenAppHumanTaskType taskType;

    /**
     * The task title for context.
     */
    private final String taskTitle;

    /**
     * The task description.
     */
    private final String taskDescription;

    /**
     * Task priority (1-10).
     */
    @Builder.Default
    private final int priority = 5;

    /**
     * Data associated with the task.
     */
    @Builder.Default
    private final Map<String, Object> taskData = Map.of();

    // ========================================================================
    // WORKFLOW INFORMATION
    // ========================================================================

    /**
     * The workflow instance ID.
     */
    private final String workflowInstanceId;

    /**
     * The workflow definition ID.
     */
    private final String workflowDefinitionId;

    /**
     * The current workflow step ID.
     */
    private final String workflowStepId;

    /**
     * The current workflow execution context.
     */
    private final ILyshraOpenAppContext workflowContext;

    /**
     * Business key for correlation.
     */
    private final String businessKey;

    /**
     * Tenant ID for multi-tenant deployments.
     */
    private final String tenantId;

    // ========================================================================
    // CANDIDATE INFORMATION
    // ========================================================================

    /**
     * Pre-configured candidate users who can be assigned.
     */
    @Builder.Default
    private final List<String> candidateUsers = List.of();

    /**
     * Pre-configured candidate groups/roles.
     */
    @Builder.Default
    private final List<String> candidateGroups = List.of();

    /**
     * Users who should be excluded from assignment.
     */
    @Builder.Default
    private final List<String> excludedUsers = List.of();

    // ========================================================================
    // STRATEGY CONFIGURATION
    // ========================================================================

    /**
     * Strategy-specific configuration.
     */
    private final AssignmentStrategyConfig strategyConfig;

    // ========================================================================
    // ADDITIONAL METADATA
    // ========================================================================

    /**
     * Additional metadata for custom strategies.
     */
    @Builder.Default
    private final Map<String, Object> metadata = Map.of();

    /**
     * The initiator/creator of the workflow (may be excluded from assignment).
     */
    private final String workflowInitiator;

    /**
     * Required capabilities/skills for the task.
     */
    @Builder.Default
    private final List<String> requiredCapabilities = List.of();

    /**
     * Department or organizational unit context.
     */
    private final String department;

    /**
     * Geographic location context.
     */
    private final String location;

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Gets a metadata value by key.
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getMetadataValue(String key) {
        return Optional.ofNullable((T) metadata.get(key));
    }

    /**
     * Gets a task data value by key.
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getTaskDataValue(String key) {
        return Optional.ofNullable((T) taskData.get(key));
    }

    /**
     * Gets a context variable from the workflow context.
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getContextVariable(String key) {
        if (workflowContext == null) {
            return Optional.empty();
        }
        return Optional.ofNullable((T) workflowContext.getVariable(key));
    }

    /**
     * Checks if a user is in the candidate list.
     */
    public boolean isCandidate(String userId) {
        return candidateUsers.contains(userId);
    }

    /**
     * Checks if a user is excluded.
     */
    public boolean isExcluded(String userId) {
        return excludedUsers.contains(userId) ||
                (workflowInitiator != null && workflowInitiator.equals(userId) &&
                        strategyConfig != null && strategyConfig.isExcludeInitiator());
    }

    /**
     * Creates a builder with common defaults pre-populated.
     */
    public static AssignmentContextBuilder withDefaults() {
        return AssignmentContext.builder()
                .priority(5)
                .taskData(Map.of())
                .candidateUsers(List.of())
                .candidateGroups(List.of())
                .excludedUsers(List.of())
                .metadata(Map.of())
                .requiredCapabilities(List.of());
    }
}
