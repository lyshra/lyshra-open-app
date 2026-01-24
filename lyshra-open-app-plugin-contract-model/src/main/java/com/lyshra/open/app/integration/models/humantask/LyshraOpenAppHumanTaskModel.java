package com.lyshra.open.app.integration.models.humantask;

import com.lyshra.open.app.integration.contract.humantask.*;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppHumanTaskStatus;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppHumanTaskType;
import lombok.Builder;
import lombok.Data;
import lombok.With;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Standard implementation of the Human Task model.
 * <p>
 * This class provides a comprehensive, standardized structure for all human tasks
 * across workflows. It is designed to be:
 * <ul>
 *   <li>Immutable (with copy-on-write via @With)</li>
 *   <li>Serializable for persistence and distribution</li>
 *   <li>Builder-based for clean construction</li>
 * </ul>
 *
 * <h2>Model Fields Summary</h2>
 *
 * <h3>Identity Fields</h3>
 * <table border="1">
 *   <tr><th>Field</th><th>Type</th><th>Description</th><th>Required</th></tr>
 *   <tr><td>taskId</td><td>String</td><td>Unique task identifier</td><td>Yes</td></tr>
 *   <tr><td>workflowInstanceId</td><td>String</td><td>Parent workflow instance</td><td>Yes</td></tr>
 *   <tr><td>workflowStepId</td><td>String</td><td>Workflow step that created task</td><td>Yes</td></tr>
 *   <tr><td>workflowDefinitionId</td><td>String</td><td>Workflow definition reference</td><td>No</td></tr>
 * </table>
 *
 * <h3>Assignment Fields</h3>
 * <table border="1">
 *   <tr><th>Field</th><th>Type</th><th>Description</th><th>Required</th></tr>
 *   <tr><td>assignees</td><td>List&lt;String&gt;</td><td>Direct assignees</td><td>No</td></tr>
 *   <tr><td>candidateGroups</td><td>List&lt;String&gt;</td><td>Groups that can claim</td><td>No</td></tr>
 *   <tr><td>claimedBy</td><td>String</td><td>User who claimed task</td><td>No</td></tr>
 *   <tr><td>owner</td><td>String</td><td>Task owner</td><td>No</td></tr>
 * </table>
 *
 * <h3>Status Fields</h3>
 * <table border="1">
 *   <tr><th>Field</th><th>Type</th><th>Description</th><th>Required</th></tr>
 *   <tr><td>status</td><td>LyshraOpenAppHumanTaskStatus</td><td>Current lifecycle state</td><td>Yes</td></tr>
 *   <tr><td>taskType</td><td>LyshraOpenAppHumanTaskType</td><td>Type of task</td><td>Yes</td></tr>
 *   <tr><td>priority</td><td>int</td><td>Priority (1-10)</td><td>Yes</td></tr>
 * </table>
 *
 * <h3>Timestamp Fields</h3>
 * <table border="1">
 *   <tr><th>Field</th><th>Type</th><th>Description</th><th>Required</th></tr>
 *   <tr><td>createdAt</td><td>Instant</td><td>Creation time</td><td>Yes</td></tr>
 *   <tr><td>updatedAt</td><td>Instant</td><td>Last update time</td><td>Yes</td></tr>
 *   <tr><td>dueAt</td><td>Instant</td><td>Due date/time</td><td>No</td></tr>
 *   <tr><td>claimedAt</td><td>Instant</td><td>When claimed</td><td>No</td></tr>
 *   <tr><td>completedAt</td><td>Instant</td><td>Completion time</td><td>No</td></tr>
 * </table>
 *
 * <h3>Decision Outcome Fields</h3>
 * <table border="1">
 *   <tr><th>Field</th><th>Type</th><th>Description</th><th>Required</th></tr>
 *   <tr><td>decisionOutcome</td><td>String</td><td>Final decision (APPROVED/REJECTED/etc)</td><td>No</td></tr>
 *   <tr><td>decisionReason</td><td>String</td><td>Reason for decision</td><td>No</td></tr>
 *   <tr><td>resolvedBy</td><td>String</td><td>User who resolved</td><td>No</td></tr>
 *   <tr><td>resultData</td><td>Map&lt;String, Object&gt;</td><td>Submitted data</td><td>No</td></tr>
 * </table>
 *
 * <h2>Usage Examples</h2>
 *
 * <h3>Creating a New Task</h3>
 * <pre>{@code
 * LyshraOpenAppHumanTaskModel task = LyshraOpenAppHumanTaskModel.builder()
 *     .taskId(UUID.randomUUID().toString())
 *     .workflowInstanceId(workflowId)
 *     .workflowStepId(stepId)
 *     .taskType(LyshraOpenAppHumanTaskType.APPROVAL)
 *     .status(LyshraOpenAppHumanTaskStatus.PENDING)
 *     .title("Approve Purchase Order")
 *     .description("Review and approve PO #12345")
 *     .priority(5)
 *     .assignees(List.of("manager@company.com"))
 *     .dueAt(Instant.now().plus(Duration.ofDays(1)))
 *     .createdAt(Instant.now())
 *     .updatedAt(Instant.now())
 *     .build();
 * }</pre>
 *
 * <h3>Updating Task Status</h3>
 * <pre>{@code
 * LyshraOpenAppHumanTaskModel approvedTask = task
 *     .withStatus(LyshraOpenAppHumanTaskStatus.APPROVED)
 *     .withDecisionOutcome("APPROVED")
 *     .withResolvedBy("manager@company.com")
 *     .withCompletedAt(Instant.now())
 *     .withUpdatedAt(Instant.now());
 * }</pre>
 *
 * @see ILyshraOpenAppHumanTask
 * @see LyshraOpenAppHumanTaskStatus
 * @see LyshraOpenAppHumanTaskType
 */
@Data
@Builder(toBuilder = true)
@With
public class LyshraOpenAppHumanTaskModel implements ILyshraOpenAppHumanTask, Serializable {

    private static final long serialVersionUID = 1L;

    // ========================================================================
    // IDENTITY FIELDS
    // ========================================================================

    /**
     * Unique task identifier.
     */
    private final String taskId;

    /**
     * Parent workflow instance identifier.
     */
    private final String workflowInstanceId;

    /**
     * Workflow step identifier that created this task.
     */
    private final String workflowStepId;

    /**
     * Workflow definition identifier for auditing.
     */
    private final String workflowDefinitionId;

    // ========================================================================
    // TASK TYPE AND CLASSIFICATION
    // ========================================================================

    /**
     * Type of human task (APPROVAL, DECISION, MANUAL_INPUT, etc.).
     */
    private final LyshraOpenAppHumanTaskType taskType;

    /**
     * Human-readable title.
     */
    private final String title;

    /**
     * Detailed description of required action.
     */
    private final String description;

    /**
     * Priority level (1-10, default 5).
     */
    @Builder.Default
    private final int priority = 5;

    // ========================================================================
    // STATUS AND LIFECYCLE
    // ========================================================================

    /**
     * Current lifecycle status.
     */
    private final LyshraOpenAppHumanTaskStatus status;

    // ========================================================================
    // ASSIGNMENT FIELDS
    // ========================================================================

    /**
     * List of directly assigned users.
     */
    @Builder.Default
    private final List<String> assignees = Collections.emptyList();

    /**
     * List of candidate groups/roles.
     */
    @Builder.Default
    private final List<String> candidateGroups = Collections.emptyList();

    /**
     * User who has claimed the task.
     */
    private final String claimedBy;

    /**
     * Task owner for accountability tracking.
     */
    private final String owner;

    // ========================================================================
    // TIMESTAMPS
    // ========================================================================

    /**
     * When the task was created.
     */
    private final Instant createdAt;

    /**
     * When the task was last updated.
     */
    private final Instant updatedAt;

    /**
     * When the task is due (deadline).
     */
    private final Instant dueAt;

    /**
     * When the task was claimed.
     */
    private final Instant claimedAt;

    /**
     * When the task was completed/resolved.
     */
    private final Instant completedAt;

    // ========================================================================
    // DECISION OUTCOME
    // ========================================================================

    /**
     * The decision outcome (APPROVED, REJECTED, COMPLETED, etc.).
     * This is the branch name used for workflow resumption.
     */
    private final String decisionOutcome;

    /**
     * Reason provided for the decision.
     */
    private final String decisionReason;

    /**
     * User who resolved/completed the task.
     */
    private final String resolvedBy;

    /**
     * Result data submitted with the decision (form data, etc.).
     */
    @Builder.Default
    private final Map<String, Object> resultData = Collections.emptyMap();

    // ========================================================================
    // TIMEOUT AND ESCALATION
    // ========================================================================

    /**
     * Timeout duration before escalation/auto-action.
     */
    private final Duration timeout;

    /**
     * Escalation configuration.
     */
    private final ILyshraOpenAppHumanTaskEscalation escalationConfig;

    // ========================================================================
    // TASK DATA AND FORM
    // ========================================================================

    /**
     * Context data for display to user.
     */
    @Builder.Default
    private final Map<String, Object> taskData = Collections.emptyMap();

    /**
     * Form schema for data collection tasks.
     */
    private final ILyshraOpenAppHumanTaskFormSchema formSchema;

    // ========================================================================
    // AUDIT AND TRACKING
    // ========================================================================

    /**
     * Comments added to the task.
     */
    @Builder.Default
    private final List<ILyshraOpenAppHumanTaskComment> comments = Collections.emptyList();

    /**
     * Audit trail of state changes.
     */
    @Builder.Default
    private final List<ILyshraOpenAppHumanTaskAuditEntry> auditTrail = Collections.emptyList();

    /**
     * Custom metadata.
     */
    @Builder.Default
    private final Map<String, Object> metadata = Collections.emptyMap();

    // ========================================================================
    // MULTI-TENANCY AND CORRELATION
    // ========================================================================

    /**
     * Tenant identifier for multi-tenant deployments.
     */
    private final String tenantId;

    /**
     * Business key for domain correlation.
     */
    private final String businessKey;

    /**
     * Correlation ID for distributed tracing.
     */
    private final String correlationId;

    // ========================================================================
    // INTERFACE IMPLEMENTATION - Optional getters
    // ========================================================================

    @Override
    public Optional<String> getWorkflowDefinitionId() {
        return Optional.ofNullable(workflowDefinitionId);
    }

    @Override
    public Optional<String> getClaimedBy() {
        return Optional.ofNullable(claimedBy);
    }

    @Override
    public Optional<String> getOwner() {
        return Optional.ofNullable(owner);
    }

    @Override
    public Optional<Instant> getDueAt() {
        return Optional.ofNullable(dueAt);
    }

    @Override
    public Optional<Instant> getClaimedAt() {
        return Optional.ofNullable(claimedAt);
    }

    @Override
    public Optional<Instant> getCompletedAt() {
        return Optional.ofNullable(completedAt);
    }

    @Override
    public Optional<String> getDecisionOutcome() {
        if (decisionOutcome != null) {
            return Optional.of(decisionOutcome);
        }
        // Fall back to deriving from status
        return ILyshraOpenAppHumanTask.super.getDecisionOutcome();
    }

    @Override
    public Optional<String> getDecisionReason() {
        return Optional.ofNullable(decisionReason);
    }

    @Override
    public Optional<String> getResolvedBy() {
        if (resolvedBy != null) {
            return Optional.of(resolvedBy);
        }
        return getClaimedBy();
    }

    @Override
    public Optional<Map<String, Object>> getResultData() {
        return resultData.isEmpty() ? Optional.empty() : Optional.of(resultData);
    }

    @Override
    public Optional<Duration> getTimeout() {
        return Optional.ofNullable(timeout);
    }

    @Override
    public Optional<ILyshraOpenAppHumanTaskEscalation> getEscalationConfig() {
        return Optional.ofNullable(escalationConfig);
    }

    @Override
    public Optional<ILyshraOpenAppHumanTaskFormSchema> getFormSchema() {
        return Optional.ofNullable(formSchema);
    }

    @Override
    public Optional<String> getTenantId() {
        return Optional.ofNullable(tenantId);
    }

    @Override
    public Optional<String> getBusinessKey() {
        return Optional.ofNullable(businessKey);
    }

    @Override
    public Optional<String> getCorrelationId() {
        return Optional.ofNullable(correlationId);
    }

    // ========================================================================
    // CONVENIENCE FACTORY METHODS
    // ========================================================================

    /**
     * Creates a new approval task.
     */
    public static LyshraOpenAppHumanTaskModel createApprovalTask(
            String workflowInstanceId,
            String workflowStepId,
            String title,
            String description,
            List<String> assignees,
            List<String> candidateGroups,
            Duration timeout) {

        Instant now = Instant.now();
        return LyshraOpenAppHumanTaskModel.builder()
                .taskId(UUID.randomUUID().toString())
                .workflowInstanceId(workflowInstanceId)
                .workflowStepId(workflowStepId)
                .taskType(LyshraOpenAppHumanTaskType.APPROVAL)
                .status(LyshraOpenAppHumanTaskStatus.PENDING)
                .title(title)
                .description(description)
                .assignees(assignees != null ? new ArrayList<>(assignees) : Collections.emptyList())
                .candidateGroups(candidateGroups != null ? new ArrayList<>(candidateGroups) : Collections.emptyList())
                .timeout(timeout)
                .dueAt(timeout != null ? now.plus(timeout) : null)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    /**
     * Creates a new decision task with options.
     */
    public static LyshraOpenAppHumanTaskModel createDecisionTask(
            String workflowInstanceId,
            String workflowStepId,
            String title,
            String description,
            List<String> candidateGroups,
            Map<String, Object> taskData,
            Duration timeout) {

        Instant now = Instant.now();
        return LyshraOpenAppHumanTaskModel.builder()
                .taskId(UUID.randomUUID().toString())
                .workflowInstanceId(workflowInstanceId)
                .workflowStepId(workflowStepId)
                .taskType(LyshraOpenAppHumanTaskType.DECISION)
                .status(LyshraOpenAppHumanTaskStatus.PENDING)
                .title(title)
                .description(description)
                .candidateGroups(candidateGroups != null ? new ArrayList<>(candidateGroups) : Collections.emptyList())
                .taskData(taskData != null ? new HashMap<>(taskData) : Collections.emptyMap())
                .timeout(timeout)
                .dueAt(timeout != null ? now.plus(timeout) : null)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    /**
     * Creates a new manual input task with form schema.
     */
    public static LyshraOpenAppHumanTaskModel createManualInputTask(
            String workflowInstanceId,
            String workflowStepId,
            String title,
            String description,
            List<String> assignees,
            ILyshraOpenAppHumanTaskFormSchema formSchema,
            Duration timeout) {

        Instant now = Instant.now();
        return LyshraOpenAppHumanTaskModel.builder()
                .taskId(UUID.randomUUID().toString())
                .workflowInstanceId(workflowInstanceId)
                .workflowStepId(workflowStepId)
                .taskType(LyshraOpenAppHumanTaskType.MANUAL_INPUT)
                .status(LyshraOpenAppHumanTaskStatus.PENDING)
                .title(title)
                .description(description)
                .assignees(assignees != null ? new ArrayList<>(assignees) : Collections.emptyList())
                .formSchema(formSchema)
                .timeout(timeout)
                .dueAt(timeout != null ? now.plus(timeout) : null)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }

    // ========================================================================
    // STATE TRANSITION HELPERS
    // ========================================================================

    /**
     * Returns a copy of this task with APPROVED status and outcome.
     */
    public LyshraOpenAppHumanTaskModel approve(String userId, String reason) {
        return this.toBuilder()
                .status(LyshraOpenAppHumanTaskStatus.APPROVED)
                .decisionOutcome("APPROVED")
                .decisionReason(reason)
                .resolvedBy(userId)
                .completedAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Returns a copy of this task with REJECTED status and outcome.
     */
    public LyshraOpenAppHumanTaskModel reject(String userId, String reason) {
        return this.toBuilder()
                .status(LyshraOpenAppHumanTaskStatus.REJECTED)
                .decisionOutcome("REJECTED")
                .decisionReason(reason)
                .resolvedBy(userId)
                .completedAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Returns a copy of this task with COMPLETED status and result data.
     */
    public LyshraOpenAppHumanTaskModel complete(String userId, Map<String, Object> data) {
        return this.toBuilder()
                .status(LyshraOpenAppHumanTaskStatus.COMPLETED)
                .decisionOutcome("COMPLETED")
                .resultData(data != null ? new HashMap<>(data) : Collections.emptyMap())
                .resolvedBy(userId)
                .completedAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Returns a copy of this task with the specified decision outcome.
     */
    public LyshraOpenAppHumanTaskModel decide(String userId, String outcome, String reason) {
        return this.toBuilder()
                .status(LyshraOpenAppHumanTaskStatus.COMPLETED)
                .decisionOutcome(outcome)
                .decisionReason(reason)
                .resolvedBy(userId)
                .completedAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Returns a copy of this task claimed by the specified user.
     */
    public LyshraOpenAppHumanTaskModel claim(String userId) {
        return this.toBuilder()
                .status(LyshraOpenAppHumanTaskStatus.IN_PROGRESS)
                .claimedBy(userId)
                .claimedAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Returns a copy of this task with the claim released.
     */
    public LyshraOpenAppHumanTaskModel unclaim() {
        return this.toBuilder()
                .status(LyshraOpenAppHumanTaskStatus.PENDING)
                .claimedBy(null)
                .claimedAt(null)
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Returns a copy of this task with CANCELLED status.
     */
    public LyshraOpenAppHumanTaskModel cancel(String userId, String reason) {
        return this.toBuilder()
                .status(LyshraOpenAppHumanTaskStatus.CANCELLED)
                .decisionOutcome("CANCELLED")
                .decisionReason(reason)
                .resolvedBy(userId)
                .completedAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Returns a copy of this task with TIMED_OUT status.
     */
    public LyshraOpenAppHumanTaskModel timeout() {
        return this.toBuilder()
                .status(LyshraOpenAppHumanTaskStatus.TIMED_OUT)
                .decisionOutcome("TIMED_OUT")
                .resolvedBy("SYSTEM")
                .completedAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Returns a copy of this task with ESCALATED status.
     */
    public LyshraOpenAppHumanTaskModel escalate(List<String> newAssignees, List<String> newGroups) {
        return this.toBuilder()
                .status(LyshraOpenAppHumanTaskStatus.ESCALATED)
                .assignees(newAssignees != null ? new ArrayList<>(newAssignees) : this.assignees)
                .candidateGroups(newGroups != null ? new ArrayList<>(newGroups) : this.candidateGroups)
                .updatedAt(Instant.now())
                .build();
    }

    /**
     * Checks if the task is in a terminal state.
     */
    public boolean isTerminal() {
        return status == LyshraOpenAppHumanTaskStatus.APPROVED
                || status == LyshraOpenAppHumanTaskStatus.REJECTED
                || status == LyshraOpenAppHumanTaskStatus.COMPLETED
                || status == LyshraOpenAppHumanTaskStatus.CANCELLED
                || status == LyshraOpenAppHumanTaskStatus.TIMED_OUT
                || status == LyshraOpenAppHumanTaskStatus.EXPIRED;
    }

    /**
     * Checks if the task is actionable (can be claimed/completed).
     */
    public boolean isActionable() {
        return status == LyshraOpenAppHumanTaskStatus.PENDING
                || status == LyshraOpenAppHumanTaskStatus.ASSIGNED
                || status == LyshraOpenAppHumanTaskStatus.IN_PROGRESS
                || status == LyshraOpenAppHumanTaskStatus.ESCALATED;
    }

    /**
     * Checks if the task is overdue.
     */
    public boolean isOverdue() {
        return dueAt != null && Instant.now().isAfter(dueAt) && !isTerminal();
    }
}
