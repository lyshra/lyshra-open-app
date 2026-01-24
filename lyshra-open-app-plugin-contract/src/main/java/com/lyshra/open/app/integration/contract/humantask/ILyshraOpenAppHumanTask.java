package com.lyshra.open.app.integration.contract.humantask;

import com.lyshra.open.app.integration.enumerations.LyshraOpenAppHumanTaskStatus;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppHumanTaskType;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Represents a human task instance within a workflow execution.
 * A human task is created when a workflow reaches a step that requires
 * manual intervention, approval, or input from a human actor.
 *
 * <h2>Human Task Model - Standardized Structure</h2>
 * <p>This interface defines the standardized structure for all human tasks across workflows.
 * It captures the following categories of information:</p>
 *
 * <h3>1. Identity Fields</h3>
 * <ul>
 *   <li>{@link #getTaskId()} - Unique task identifier</li>
 *   <li>{@link #getWorkflowInstanceId()} - Parent workflow instance</li>
 *   <li>{@link #getWorkflowStepId()} - Workflow step that created the task</li>
 *   <li>{@link #getWorkflowDefinitionId()} - Workflow definition reference</li>
 * </ul>
 *
 * <h3>2. Assignment Fields</h3>
 * <ul>
 *   <li>{@link #getAssignees()} - Specific users who can act</li>
 *   <li>{@link #getCandidateGroups()} - Groups/roles who can act</li>
 *   <li>{@link #getClaimedBy()} - User who has claimed the task</li>
 *   <li>{@link #getOwner()} - Task owner for tracking</li>
 * </ul>
 *
 * <h3>3. Status and Lifecycle</h3>
 * <ul>
 *   <li>{@link #getStatus()} - Current lifecycle state</li>
 *   <li>{@link #getTaskType()} - Type of human task</li>
 *   <li>{@link #getPriority()} - Task priority level</li>
 * </ul>
 *
 * <h3>4. Timestamps</h3>
 * <ul>
 *   <li>{@link #getCreatedAt()} - When task was created</li>
 *   <li>{@link #getUpdatedAt()} - When task was last updated</li>
 *   <li>{@link #getDueAt()} - When task is due</li>
 *   <li>{@link #getCompletedAt()} - When task was completed</li>
 *   <li>{@link #getClaimedAt()} - When task was claimed</li>
 * </ul>
 *
 * <h3>5. Decision Outcome</h3>
 * <ul>
 *   <li>{@link #getDecisionOutcome()} - The decision made (APPROVED, REJECTED, etc.)</li>
 *   <li>{@link #getResultData()} - Data submitted with the decision</li>
 *   <li>{@link #getDecisionReason()} - Reason provided for the decision</li>
 *   <li>{@link #getResolvedBy()} - User who resolved the task</li>
 * </ul>
 *
 * <h3>6. Audit and Tracking</h3>
 * <ul>
 *   <li>{@link #getComments()} - Comments added to the task</li>
 *   <li>{@link #getAuditTrail()} - Complete audit history</li>
 *   <li>{@link #getMetadata()} - Custom extensible metadata</li>
 * </ul>
 *
 * <h2>Task Lifecycle State Machine</h2>
 * <pre>
 *                                ┌─────────────┐
 *                                │   CREATED   │
 *                                └──────┬──────┘
 *                                       │
 *                                       ▼
 *                                ┌─────────────┐
 *                         ┌──────│   PENDING   │──────┐
 *                         │      └──────┬──────┘      │
 *                         │             │             │
 *                         │             ▼             │
 *                         │      ┌─────────────┐      │
 *                         │      │  ASSIGNED   │      │
 *                         │      └──────┬──────┘      │
 *                         │             │             │
 *                         │             ▼             │
 *                         │      ┌─────────────┐      │
 *                         │      │ IN_PROGRESS │      │
 *                         │      └──────┬──────┘      │
 *                         │             │             │
 *              ┌──────────┼─────────────┼─────────────┼──────────┐
 *              │          │             │             │          │
 *              ▼          ▼             ▼             ▼          ▼
 *       ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐
 *       │ APPROVED │ │ REJECTED │ │COMPLETED │ │TIMED_OUT │ │CANCELLED │
 *       └──────────┘ └──────────┘ └──────────┘ └────┬─────┘ └──────────┘
 *                                                   │
 *                                                   ▼
 *                                            ┌──────────┐
 *                                            │ESCALATED │
 *                                            └──────────┘
 * </pre>
 *
 * <h2>Lifecycle Transitions</h2>
 * <table border="1">
 *   <tr><th>From State</th><th>To State</th><th>Trigger</th></tr>
 *   <tr><td>CREATED</td><td>PENDING</td><td>Task initialization complete</td></tr>
 *   <tr><td>PENDING</td><td>ASSIGNED</td><td>Task assigned to specific user</td></tr>
 *   <tr><td>PENDING/ASSIGNED</td><td>IN_PROGRESS</td><td>User claims the task</td></tr>
 *   <tr><td>IN_PROGRESS</td><td>APPROVED</td><td>User approves</td></tr>
 *   <tr><td>IN_PROGRESS</td><td>REJECTED</td><td>User rejects</td></tr>
 *   <tr><td>IN_PROGRESS</td><td>COMPLETED</td><td>User submits form data</td></tr>
 *   <tr><td>ANY</td><td>CANCELLED</td><td>Task cancelled by system/user</td></tr>
 *   <tr><td>PENDING/ASSIGNED/IN_PROGRESS</td><td>TIMED_OUT</td><td>Timeout reached</td></tr>
 *   <tr><td>TIMED_OUT</td><td>ESCALATED</td><td>Escalation triggered</td></tr>
 * </table>
 *
 * @see LyshraOpenAppHumanTaskStatus
 * @see LyshraOpenAppHumanTaskType
 * @see ILyshraOpenAppHumanTaskAuditEntry
 */
public interface ILyshraOpenAppHumanTask {

    // ========================================================================
    // IDENTITY FIELDS
    // ========================================================================

    /**
     * Unique identifier for this human task instance.
     * <p>Format: UUID or custom format based on configuration.</p>
     * <p>Example: "task-550e8400-e29b-41d4-a716-446655440000"</p>
     *
     * @return the unique task identifier, never null
     */
    String getTaskId();

    /**
     * Identifier of the workflow instance that created this task.
     * <p>Links this task back to its parent workflow execution for
     * resumption and correlation purposes.</p>
     *
     * @return the workflow instance identifier, never null
     */
    String getWorkflowInstanceId();

    /**
     * Identifier of the workflow step that created this task.
     * <p>Identifies which step in the workflow definition created this task.
     * Used for workflow resumption to determine next steps.</p>
     *
     * @return the workflow step identifier, never null
     */
    String getWorkflowStepId();

    /**
     * The workflow definition identifier.
     * <p>Provides reference to the workflow definition for auditing
     * and reporting purposes.</p>
     *
     * @return the workflow definition identifier, may be empty
     */
    default Optional<String> getWorkflowDefinitionId() {
        return Optional.empty();
    }

    // ========================================================================
    // TASK TYPE AND CLASSIFICATION
    // ========================================================================

    /**
     * The type of human task.
     * <p>Determines the expected interaction pattern:</p>
     * <ul>
     *   <li>APPROVAL - Binary approve/reject decision</li>
     *   <li>DECISION - Multi-option selection</li>
     *   <li>MANUAL_INPUT - Form-based data collection</li>
     *   <li>REVIEW - Content review and feedback</li>
     *   <li>CONFIRMATION - Simple confirmation step</li>
     * </ul>
     *
     * @return the human task type, never null
     */
    LyshraOpenAppHumanTaskType getTaskType();

    /**
     * Human-readable title for display purposes.
     * <p>Should be concise and descriptive for task list display.</p>
     *
     * @return the task title, never null
     */
    String getTitle();

    /**
     * Detailed description of what action is required.
     * <p>Provides context and instructions for the human actor.</p>
     *
     * @return the task description, may be null or empty
     */
    String getDescription();

    /**
     * Priority of the task.
     * <p>Scale: 1 (lowest) to 10 (highest). Default is 5 (normal).</p>
     * <p>Used for sorting tasks in user interfaces and escalation logic.</p>
     *
     * @return the priority level (1-10)
     */
    int getPriority();

    // ========================================================================
    // STATUS AND LIFECYCLE
    // ========================================================================

    /**
     * Current status of the human task in its lifecycle.
     * <p>Indicates where the task is in its state machine.
     * See class documentation for complete state transition diagram.</p>
     *
     * @return the current task status, never null
     * @see LyshraOpenAppHumanTaskStatus
     */
    LyshraOpenAppHumanTaskStatus getStatus();

    // ========================================================================
    // ASSIGNMENT FIELDS
    // ========================================================================

    /**
     * List of specific users who can act on this task.
     * <p>Direct assignment - these users see the task in their personal inbox.</p>
     *
     * @return list of assignee user identifiers, never null (may be empty)
     */
    List<String> getAssignees();

    /**
     * List of groups/roles who can act on this task.
     * <p>Group assignment - users in these groups see the task in their group inbox.
     * Any member of a candidate group can claim the task.</p>
     *
     * @return list of candidate group identifiers, never null (may be empty)
     */
    List<String> getCandidateGroups();

    /**
     * User who has claimed the task (if any).
     * <p>When a task is claimed, only the claiming user can complete it
     * (until they unclaim or delegate it).</p>
     *
     * @return the claiming user identifier, empty if not claimed
     */
    Optional<String> getClaimedBy();

    /**
     * The owner of the task for tracking and reporting.
     * <p>Owner is typically the user who will be accountable for completion,
     * which may differ from the current assignee.</p>
     *
     * @return the task owner identifier, empty if not set
     */
    default Optional<String> getOwner() {
        return Optional.empty();
    }

    // ========================================================================
    // TIMESTAMPS
    // ========================================================================

    /**
     * When the task was created.
     * <p>Set automatically when the task is first persisted.</p>
     *
     * @return the creation timestamp, never null
     */
    Instant getCreatedAt();

    /**
     * When the task was last updated.
     * <p>Updated on any state change, data update, or comment addition.</p>
     *
     * @return the last update timestamp, never null
     */
    Instant getUpdatedAt();

    /**
     * When the task is due.
     * <p>The deadline for completing this task. Tasks past due may
     * be highlighted in UIs and may trigger escalation.</p>
     *
     * @return the due date/time, empty if no deadline
     */
    Optional<Instant> getDueAt();

    /**
     * When the task is due (alias for getDueAt for compatibility).
     *
     * @return the due date/time, empty if no deadline
     */
    default Optional<Instant> getDueDate() {
        return getDueAt();
    }

    /**
     * When the task was claimed by a user.
     *
     * @return the claim timestamp, empty if not claimed
     */
    default Optional<Instant> getClaimedAt() {
        return Optional.empty();
    }

    /**
     * When the task was completed (resolved).
     * <p>Set when the task transitions to a terminal state
     * (APPROVED, REJECTED, COMPLETED, CANCELLED, TIMED_OUT).</p>
     *
     * @return the completion timestamp, empty if not completed
     */
    Optional<Instant> getCompletedAt();

    // ========================================================================
    // DECISION OUTCOME
    // ========================================================================

    /**
     * The decision outcome of the human task.
     * <p>Represents the final decision made by the human actor. Common values:</p>
     * <ul>
     *   <li>"APPROVED" - Task was approved</li>
     *   <li>"REJECTED" - Task was rejected</li>
     *   <li>"COMPLETED" - Task was completed with data</li>
     *   <li>"CANCELLED" - Task was cancelled</li>
     *   <li>"TIMED_OUT" - Task timed out</li>
     *   <li>Custom values for DECISION type tasks</li>
     * </ul>
     * <p>This value is used to determine the workflow branch to follow
     * when resuming execution after the human task.</p>
     *
     * @return the decision outcome, empty if task not yet resolved
     */
    default Optional<String> getDecisionOutcome() {
        // Derive from status if not explicitly set
        LyshraOpenAppHumanTaskStatus status = getStatus();
        if (status == null) {
            return Optional.empty();
        }
        return switch (status) {
            case APPROVED -> Optional.of("APPROVED");
            case REJECTED -> Optional.of("REJECTED");
            case COMPLETED -> Optional.of("COMPLETED");
            case CANCELLED -> Optional.of("CANCELLED");
            case TIMED_OUT -> Optional.of("TIMED_OUT");
            case ESCALATED -> Optional.of("ESCALATED");
            default -> Optional.empty();
        };
    }

    /**
     * The reason provided for the decision.
     * <p>Optional explanation or justification for the decision made.
     * Useful for audit and review purposes.</p>
     *
     * @return the decision reason, empty if not provided
     */
    default Optional<String> getDecisionReason() {
        return Optional.empty();
    }

    /**
     * User who resolved (completed) the task.
     * <p>The user who made the final decision. May be system/timer
     * for auto-resolved tasks.</p>
     *
     * @return the resolving user identifier, empty if not resolved
     */
    default Optional<String> getResolvedBy() {
        return getClaimedBy();
    }

    /**
     * Result data provided by the human actor.
     * <p>Contains form submission data or other structured output
     * from the human task. This data is typically merged into
     * the workflow context for subsequent steps.</p>
     *
     * @return the result data map, empty if no data provided
     */
    Optional<Map<String, Object>> getResultData();

    // ========================================================================
    // TIMEOUT AND ESCALATION
    // ========================================================================

    /**
     * Timeout duration after which the task will be auto-escalated or failed.
     * <p>Calculated from task creation time. When expired:</p>
     * <ul>
     *   <li>If escalation configured: escalation is triggered</li>
     *   <li>If auto-approve configured: task is auto-approved</li>
     *   <li>If auto-reject configured: task is auto-rejected</li>
     *   <li>Otherwise: task transitions to TIMED_OUT</li>
     * </ul>
     *
     * @return the timeout duration, empty if no timeout
     */
    Optional<Duration> getTimeout();

    /**
     * Configuration for escalation behavior on timeout.
     *
     * @return the escalation configuration, empty if no escalation
     */
    Optional<ILyshraOpenAppHumanTaskEscalation> getEscalationConfig();

    // ========================================================================
    // TASK DATA AND FORM
    // ========================================================================

    /**
     * Data payload associated with the task for display to the user.
     * <p>Context data from the workflow that helps the human make a decision.
     * May include order details, customer information, etc.</p>
     *
     * @return the task data map, never null (may be empty)
     */
    Map<String, Object> getTaskData();

    /**
     * Form schema for capturing user input (if applicable).
     * <p>Defines the fields, validation rules, and layout for
     * tasks that require data input (MANUAL_INPUT type).</p>
     *
     * @return the form schema, empty if no form required
     */
    Optional<ILyshraOpenAppHumanTaskFormSchema> getFormSchema();

    // ========================================================================
    // AUDIT AND TRACKING
    // ========================================================================

    /**
     * Comments or notes added during task processing.
     * <p>Chronological list of comments from users and system.
     * May include internal notes not visible to all users.</p>
     *
     * @return list of comments, never null (may be empty)
     */
    List<ILyshraOpenAppHumanTaskComment> getComments();

    /**
     * Audit trail of status changes and actions.
     * <p>Complete history of all state transitions and actions
     * performed on this task. Used for compliance and debugging.</p>
     *
     * @return list of audit entries, never null (may be empty)
     */
    List<ILyshraOpenAppHumanTaskAuditEntry> getAuditTrail();

    /**
     * Custom metadata for extensibility.
     * <p>Key-value pairs for storing additional data not covered
     * by the standard fields. Use for integration-specific data.</p>
     *
     * @return the metadata map, never null (may be empty)
     */
    Map<String, Object> getMetadata();

    // ========================================================================
    // MULTI-TENANCY AND CORRELATION
    // ========================================================================

    /**
     * Tenant identifier for multi-tenant deployments.
     *
     * @return the tenant identifier, empty if single-tenant
     */
    default Optional<String> getTenantId() {
        return Optional.empty();
    }

    /**
     * Business key for domain-specific correlation.
     * <p>A meaningful business identifier (e.g., order number, case ID)
     * that can be used to find related tasks.</p>
     *
     * @return the business key, empty if not set
     */
    default Optional<String> getBusinessKey() {
        return Optional.empty();
    }

    /**
     * Correlation ID for distributed tracing.
     * <p>Used to correlate this task with external requests
     * and other system events for end-to-end tracing.</p>
     *
     * @return the correlation ID, empty if not set
     */
    default Optional<String> getCorrelationId() {
        return Optional.empty();
    }
}
