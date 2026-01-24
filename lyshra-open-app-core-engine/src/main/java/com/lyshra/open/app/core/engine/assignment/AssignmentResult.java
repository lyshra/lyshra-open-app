package com.lyshra.open.app.core.engine.assignment;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Result of a task assignment strategy execution.
 *
 * <p>Contains the determined assignees, candidate groups, and any
 * metadata about the assignment decision.</p>
 *
 * <h2>Assignment Types</h2>
 * <ul>
 *   <li><b>Direct Assignment</b> - Task assigned to specific users (assignees list)</li>
 *   <li><b>Group Assignment</b> - Task available to group members (candidateGroups list)</li>
 *   <li><b>Mixed Assignment</b> - Both direct and group assignment</li>
 *   <li><b>Deferred</b> - Assignment deferred to later time</li>
 *   <li><b>Failed</b> - No suitable assignees found</li>
 * </ul>
 */
@Data
@Builder
public class AssignmentResult {

    /**
     * The type of assignment result.
     */
    public enum ResultType {
        /** Task assigned to specific users */
        ASSIGNED,
        /** Task available to candidate groups */
        CANDIDATE_GROUPS,
        /** Both direct and group assignment */
        MIXED,
        /** Assignment deferred */
        DEFERRED,
        /** No assignees found */
        NO_ASSIGNEES,
        /** Assignment failed */
        FAILED
    }

    /**
     * The result type.
     */
    private final ResultType resultType;

    /**
     * List of directly assigned users.
     */
    @Builder.Default
    private final List<String> assignees = List.of();

    /**
     * List of candidate groups.
     */
    @Builder.Default
    private final List<String> candidateGroups = List.of();

    /**
     * Primary assignee (if single assignment).
     */
    private final String primaryAssignee;

    /**
     * The strategy that produced this result.
     */
    private final String strategyName;

    /**
     * Reason for the assignment decision.
     */
    private final String reason;

    /**
     * Additional metadata about the assignment.
     */
    @Builder.Default
    private final Map<String, Object> metadata = Map.of();

    /**
     * Error message if assignment failed.
     */
    private final String errorMessage;

    /**
     * Suggested reassignment time (for deferred assignments).
     */
    private final java.time.Instant reassignAt;

    /**
     * Priority adjustment based on assignment.
     */
    private final Integer priorityAdjustment;

    // ========================================================================
    // FACTORY METHODS
    // ========================================================================

    /**
     * Creates a result with direct assignment to specific users.
     */
    public static AssignmentResult assignTo(List<String> assignees) {
        return AssignmentResult.builder()
                .resultType(ResultType.ASSIGNED)
                .assignees(assignees)
                .primaryAssignee(assignees.isEmpty() ? null : assignees.get(0))
                .build();
    }

    /**
     * Creates a result with direct assignment to a single user.
     */
    public static AssignmentResult assignTo(String assignee) {
        return AssignmentResult.builder()
                .resultType(ResultType.ASSIGNED)
                .assignees(List.of(assignee))
                .primaryAssignee(assignee)
                .build();
    }

    /**
     * Creates a result with direct assignment and reason.
     */
    public static AssignmentResult assignTo(List<String> assignees, String reason) {
        return AssignmentResult.builder()
                .resultType(ResultType.ASSIGNED)
                .assignees(assignees)
                .primaryAssignee(assignees.isEmpty() ? null : assignees.get(0))
                .reason(reason)
                .build();
    }

    /**
     * Creates a result with candidate groups.
     */
    public static AssignmentResult toGroups(List<String> groups) {
        return AssignmentResult.builder()
                .resultType(ResultType.CANDIDATE_GROUPS)
                .candidateGroups(groups)
                .build();
    }

    /**
     * Creates a result with candidate groups and reason.
     */
    public static AssignmentResult toGroups(List<String> groups, String reason) {
        return AssignmentResult.builder()
                .resultType(ResultType.CANDIDATE_GROUPS)
                .candidateGroups(groups)
                .reason(reason)
                .build();
    }

    /**
     * Creates a mixed assignment result.
     */
    public static AssignmentResult mixed(List<String> assignees, List<String> groups) {
        return AssignmentResult.builder()
                .resultType(ResultType.MIXED)
                .assignees(assignees)
                .candidateGroups(groups)
                .primaryAssignee(assignees.isEmpty() ? null : assignees.get(0))
                .build();
    }

    /**
     * Creates a deferred assignment result.
     */
    public static AssignmentResult deferred(String reason, java.time.Instant reassignAt) {
        return AssignmentResult.builder()
                .resultType(ResultType.DEFERRED)
                .reason(reason)
                .reassignAt(reassignAt)
                .build();
    }

    /**
     * Creates a no-assignees result.
     */
    public static AssignmentResult noAssignees(String reason) {
        return AssignmentResult.builder()
                .resultType(ResultType.NO_ASSIGNEES)
                .reason(reason)
                .build();
    }

    /**
     * Creates a failed assignment result.
     */
    public static AssignmentResult failed(String errorMessage) {
        return AssignmentResult.builder()
                .resultType(ResultType.FAILED)
                .errorMessage(errorMessage)
                .build();
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Returns true if the assignment was successful.
     */
    public boolean isSuccessful() {
        return resultType == ResultType.ASSIGNED ||
                resultType == ResultType.CANDIDATE_GROUPS ||
                resultType == ResultType.MIXED;
    }

    /**
     * Returns true if the result has direct assignees.
     */
    public boolean hasAssignees() {
        return assignees != null && !assignees.isEmpty();
    }

    /**
     * Returns true if the result has candidate groups.
     */
    public boolean hasCandidateGroups() {
        return candidateGroups != null && !candidateGroups.isEmpty();
    }

    /**
     * Gets the primary assignee if available.
     */
    public Optional<String> getPrimaryAssigneeOptional() {
        return Optional.ofNullable(primaryAssignee);
    }

    /**
     * Gets a metadata value.
     */
    @SuppressWarnings("unchecked")
    public <T> Optional<T> getMetadataValue(String key) {
        return Optional.ofNullable((T) metadata.get(key));
    }

    /**
     * Creates a builder from this result for modifications.
     */
    public AssignmentResultBuilder toBuilder() {
        return AssignmentResult.builder()
                .resultType(this.resultType)
                .assignees(this.assignees)
                .candidateGroups(this.candidateGroups)
                .primaryAssignee(this.primaryAssignee)
                .strategyName(this.strategyName)
                .reason(this.reason)
                .metadata(this.metadata)
                .errorMessage(this.errorMessage)
                .reassignAt(this.reassignAt)
                .priorityAdjustment(this.priorityAdjustment);
    }

    /**
     * Returns a copy with the strategy name set.
     */
    public AssignmentResult withStrategyName(String strategyName) {
        return this.toBuilder().strategyName(strategyName).build();
    }
}
