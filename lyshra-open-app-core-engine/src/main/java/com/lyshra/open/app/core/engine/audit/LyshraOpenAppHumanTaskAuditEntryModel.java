package com.lyshra.open.app.core.engine.audit;

import com.lyshra.open.app.integration.contract.humantask.ILyshraOpenAppHumanTaskAuditEntry;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppHumanTaskStatus;
import lombok.Builder;
import lombok.Data;
import lombok.With;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Implementation of the human task audit entry.
 * Provides comprehensive audit trail for compliance and debugging.
 *
 * <h2>Audit Information Captured</h2>
 * <ul>
 *   <li>Who - Actor ID and type (user, system, timer)</li>
 *   <li>What - Action performed and description</li>
 *   <li>When - Timestamp of the action</li>
 *   <li>Context - Previous/new status, additional data</li>
 *   <li>Traceability - Correlation ID, client info</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * LyshraOpenAppHumanTaskAuditEntryModel entry = LyshraOpenAppHumanTaskAuditEntryModel.builder()
 *     .taskId("task-123")
 *     .action(AuditAction.APPROVED)
 *     .actorId("user@company.com")
 *     .actorType(ActorType.USER)
 *     .previousStatus(LyshraOpenAppHumanTaskStatus.IN_PROGRESS)
 *     .newStatus(LyshraOpenAppHumanTaskStatus.APPROVED)
 *     .description("Task approved by manager")
 *     .build();
 * }</pre>
 */
@Data
@Builder(toBuilder = true)
@With
public class LyshraOpenAppHumanTaskAuditEntryModel implements ILyshraOpenAppHumanTaskAuditEntry, Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * Unique identifier for this audit entry.
     */
    @Builder.Default
    private final String entryId = UUID.randomUUID().toString();

    /**
     * The task ID this audit entry belongs to.
     */
    private final String taskId;

    /**
     * The workflow instance ID for correlation.
     */
    private final String workflowInstanceId;

    /**
     * When this event occurred.
     */
    @Builder.Default
    private final Instant timestamp = Instant.now();

    /**
     * The action that was performed.
     */
    private final AuditAction action;

    /**
     * Previous status before the action (if status changed).
     */
    private final LyshraOpenAppHumanTaskStatus previousStatus;

    /**
     * New status after the action (if status changed).
     */
    private final LyshraOpenAppHumanTaskStatus newStatus;

    /**
     * User or system that performed the action.
     */
    private final String actorId;

    /**
     * Type of actor (USER, SYSTEM, TIMER, etc.).
     */
    @Builder.Default
    private final ActorType actorType = ActorType.USER;

    /**
     * Human-readable description of the action.
     */
    private final String description;

    /**
     * Additional data associated with this action.
     */
    @Builder.Default
    private final Map<String, Object> data = Map.of();

    /**
     * IP address or client identifier for security audit.
     */
    private final String clientInfo;

    /**
     * Correlation ID for distributed tracing.
     */
    private final String correlationId;

    /**
     * Reason provided for the action (e.g., rejection reason).
     */
    private final String reason;

    /**
     * Duration of the action (for performance tracking).
     */
    private final Long durationMs;

    /**
     * Result of the action (success, failure, etc.).
     */
    @Builder.Default
    private final String result = "SUCCESS";

    /**
     * Error message if the action failed.
     */
    private final String errorMessage;

    /**
     * Tenant ID for multi-tenant environments.
     */
    private final String tenantId;

    /**
     * Session ID for user session tracking.
     */
    private final String sessionId;

    @Override
    public Optional<LyshraOpenAppHumanTaskStatus> getPreviousStatus() {
        return Optional.ofNullable(previousStatus);
    }

    @Override
    public Optional<LyshraOpenAppHumanTaskStatus> getNewStatus() {
        return Optional.ofNullable(newStatus);
    }

    @Override
    public Optional<String> getClientInfo() {
        return Optional.ofNullable(clientInfo);
    }

    @Override
    public Optional<String> getCorrelationId() {
        return Optional.ofNullable(correlationId);
    }

    // ========================================================================
    // FACTORY METHODS FOR COMMON AUDIT EVENTS
    // ========================================================================

    /**
     * Creates an audit entry for task creation.
     */
    public static LyshraOpenAppHumanTaskAuditEntryModel taskCreated(
            String taskId, String workflowInstanceId, String actorId, Map<String, Object> data) {
        return LyshraOpenAppHumanTaskAuditEntryModel.builder()
                .taskId(taskId)
                .workflowInstanceId(workflowInstanceId)
                .action(AuditAction.CREATED)
                .newStatus(LyshraOpenAppHumanTaskStatus.PENDING)
                .actorId(actorId)
                .actorType(ActorType.WORKFLOW_ENGINE)
                .description("Task created")
                .data(data != null ? data : Map.of())
                .build();
    }

    /**
     * Creates an audit entry for task assignment.
     */
    public static LyshraOpenAppHumanTaskAuditEntryModel taskAssigned(
            String taskId, String workflowInstanceId, String actorId,
            String assignee, LyshraOpenAppHumanTaskStatus previousStatus) {
        return LyshraOpenAppHumanTaskAuditEntryModel.builder()
                .taskId(taskId)
                .workflowInstanceId(workflowInstanceId)
                .action(AuditAction.ASSIGNED)
                .previousStatus(previousStatus)
                .newStatus(LyshraOpenAppHumanTaskStatus.ASSIGNED)
                .actorId(actorId)
                .actorType(ActorType.SYSTEM)
                .description("Task assigned to " + assignee)
                .data(Map.of("assignee", assignee))
                .build();
    }

    /**
     * Creates an audit entry for task claim.
     */
    public static LyshraOpenAppHumanTaskAuditEntryModel taskClaimed(
            String taskId, String workflowInstanceId, String userId,
            LyshraOpenAppHumanTaskStatus previousStatus) {
        return LyshraOpenAppHumanTaskAuditEntryModel.builder()
                .taskId(taskId)
                .workflowInstanceId(workflowInstanceId)
                .action(AuditAction.CLAIMED)
                .previousStatus(previousStatus)
                .newStatus(LyshraOpenAppHumanTaskStatus.IN_PROGRESS)
                .actorId(userId)
                .actorType(ActorType.USER)
                .description("Task claimed by " + userId)
                .build();
    }

    /**
     * Creates an audit entry for task unclaim.
     */
    public static LyshraOpenAppHumanTaskAuditEntryModel taskUnclaimed(
            String taskId, String workflowInstanceId, String userId,
            LyshraOpenAppHumanTaskStatus previousStatus) {
        return LyshraOpenAppHumanTaskAuditEntryModel.builder()
                .taskId(taskId)
                .workflowInstanceId(workflowInstanceId)
                .action(AuditAction.UNCLAIMED)
                .previousStatus(previousStatus)
                .newStatus(LyshraOpenAppHumanTaskStatus.PENDING)
                .actorId(userId)
                .actorType(ActorType.USER)
                .description("Task released by " + userId)
                .build();
    }

    /**
     * Creates an audit entry for task approval.
     */
    public static LyshraOpenAppHumanTaskAuditEntryModel taskApproved(
            String taskId, String workflowInstanceId, String userId,
            LyshraOpenAppHumanTaskStatus previousStatus, String reason) {
        return LyshraOpenAppHumanTaskAuditEntryModel.builder()
                .taskId(taskId)
                .workflowInstanceId(workflowInstanceId)
                .action(AuditAction.APPROVED)
                .previousStatus(previousStatus)
                .newStatus(LyshraOpenAppHumanTaskStatus.APPROVED)
                .actorId(userId)
                .actorType(ActorType.USER)
                .description("Task approved by " + userId)
                .reason(reason)
                .build();
    }

    /**
     * Creates an audit entry for task rejection.
     */
    public static LyshraOpenAppHumanTaskAuditEntryModel taskRejected(
            String taskId, String workflowInstanceId, String userId,
            LyshraOpenAppHumanTaskStatus previousStatus, String reason) {
        return LyshraOpenAppHumanTaskAuditEntryModel.builder()
                .taskId(taskId)
                .workflowInstanceId(workflowInstanceId)
                .action(AuditAction.REJECTED)
                .previousStatus(previousStatus)
                .newStatus(LyshraOpenAppHumanTaskStatus.REJECTED)
                .actorId(userId)
                .actorType(ActorType.USER)
                .description("Task rejected by " + userId)
                .reason(reason)
                .build();
    }

    /**
     * Creates an audit entry for task completion.
     */
    public static LyshraOpenAppHumanTaskAuditEntryModel taskCompleted(
            String taskId, String workflowInstanceId, String userId,
            LyshraOpenAppHumanTaskStatus previousStatus, Map<String, Object> resultData) {
        return LyshraOpenAppHumanTaskAuditEntryModel.builder()
                .taskId(taskId)
                .workflowInstanceId(workflowInstanceId)
                .action(AuditAction.COMPLETED)
                .previousStatus(previousStatus)
                .newStatus(LyshraOpenAppHumanTaskStatus.COMPLETED)
                .actorId(userId)
                .actorType(ActorType.USER)
                .description("Task completed by " + userId)
                .data(resultData != null ? resultData : Map.of())
                .build();
    }

    /**
     * Creates an audit entry for task cancellation.
     */
    public static LyshraOpenAppHumanTaskAuditEntryModel taskCancelled(
            String taskId, String workflowInstanceId, String userId,
            LyshraOpenAppHumanTaskStatus previousStatus, String reason) {
        return LyshraOpenAppHumanTaskAuditEntryModel.builder()
                .taskId(taskId)
                .workflowInstanceId(workflowInstanceId)
                .action(AuditAction.CANCELLED)
                .previousStatus(previousStatus)
                .newStatus(LyshraOpenAppHumanTaskStatus.CANCELLED)
                .actorId(userId)
                .actorType(ActorType.USER)
                .description("Task cancelled by " + userId)
                .reason(reason)
                .build();
    }

    /**
     * Creates an audit entry for task escalation.
     */
    public static LyshraOpenAppHumanTaskAuditEntryModel taskEscalated(
            String taskId, String workflowInstanceId, String reason,
            LyshraOpenAppHumanTaskStatus previousStatus) {
        return LyshraOpenAppHumanTaskAuditEntryModel.builder()
                .taskId(taskId)
                .workflowInstanceId(workflowInstanceId)
                .action(AuditAction.ESCALATED)
                .previousStatus(previousStatus)
                .newStatus(LyshraOpenAppHumanTaskStatus.ESCALATED)
                .actorId("system")
                .actorType(ActorType.ESCALATION_HANDLER)
                .description("Task escalated")
                .reason(reason)
                .build();
    }

    /**
     * Creates an audit entry for task timeout.
     */
    public static LyshraOpenAppHumanTaskAuditEntryModel taskTimedOut(
            String taskId, String workflowInstanceId,
            LyshraOpenAppHumanTaskStatus previousStatus) {
        return LyshraOpenAppHumanTaskAuditEntryModel.builder()
                .taskId(taskId)
                .workflowInstanceId(workflowInstanceId)
                .action(AuditAction.TIMEOUT)
                .previousStatus(previousStatus)
                .newStatus(LyshraOpenAppHumanTaskStatus.TIMED_OUT)
                .actorId("system")
                .actorType(ActorType.TIMER)
                .description("Task timed out")
                .build();
    }

    /**
     * Creates an audit entry for task delegation.
     */
    public static LyshraOpenAppHumanTaskAuditEntryModel taskDelegated(
            String taskId, String workflowInstanceId, String fromUserId, String toUserId) {
        return LyshraOpenAppHumanTaskAuditEntryModel.builder()
                .taskId(taskId)
                .workflowInstanceId(workflowInstanceId)
                .action(AuditAction.DELEGATED)
                .actorId(fromUserId)
                .actorType(ActorType.USER)
                .description("Task delegated from " + fromUserId + " to " + toUserId)
                .data(Map.of("fromUser", fromUserId, "toUser", toUserId))
                .build();
    }

    /**
     * Creates an audit entry for task reassignment.
     */
    public static LyshraOpenAppHumanTaskAuditEntryModel taskReassigned(
            String taskId, String workflowInstanceId, String actorId,
            java.util.List<String> newAssignees, String reason) {
        return LyshraOpenAppHumanTaskAuditEntryModel.builder()
                .taskId(taskId)
                .workflowInstanceId(workflowInstanceId)
                .action(AuditAction.REASSIGNED)
                .actorId(actorId)
                .actorType(ActorType.USER)
                .description("Task reassigned to " + String.join(", ", newAssignees))
                .data(Map.of("newAssignees", newAssignees))
                .reason(reason)
                .build();
    }

    /**
     * Creates an audit entry for priority change.
     */
    public static LyshraOpenAppHumanTaskAuditEntryModel priorityChanged(
            String taskId, String workflowInstanceId, String actorId,
            int oldPriority, int newPriority) {
        return LyshraOpenAppHumanTaskAuditEntryModel.builder()
                .taskId(taskId)
                .workflowInstanceId(workflowInstanceId)
                .action(AuditAction.PRIORITY_CHANGED)
                .actorId(actorId)
                .actorType(ActorType.USER)
                .description("Priority changed from " + oldPriority + " to " + newPriority)
                .data(Map.of("oldPriority", oldPriority, "newPriority", newPriority))
                .build();
    }

    /**
     * Creates an audit entry for comment addition.
     */
    public static LyshraOpenAppHumanTaskAuditEntryModel commentAdded(
            String taskId, String workflowInstanceId, String userId,
            String commentContent, boolean isInternal) {
        return LyshraOpenAppHumanTaskAuditEntryModel.builder()
                .taskId(taskId)
                .workflowInstanceId(workflowInstanceId)
                .action(AuditAction.COMMENTED)
                .actorId(userId)
                .actorType(ActorType.USER)
                .description("Comment added by " + userId)
                .data(Map.of("isInternal", isInternal))
                .build();
    }
}
