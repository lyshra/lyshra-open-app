package com.lyshra.open.app.core.engine.rest.dto;

import com.lyshra.open.app.integration.enumerations.LyshraOpenAppHumanTaskStatus;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppHumanTaskType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;

/**
 * Request DTO for querying tasks with various filters.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskQueryRequest {

    /**
     * Filter by task statuses.
     */
    private List<LyshraOpenAppHumanTaskStatus> statuses;

    /**
     * Filter by task type.
     */
    private LyshraOpenAppHumanTaskType taskType;

    /**
     * Filter by workflow instance ID.
     */
    private String workflowInstanceId;

    /**
     * Filter by workflow definition ID.
     */
    private String workflowDefinitionId;

    /**
     * Filter by assignee user ID.
     */
    private String assigneeUserId;

    /**
     * Filter by claimed user ID.
     */
    private String claimedByUserId;

    /**
     * User ID for fetching pending tasks (includes group membership).
     */
    private String pendingForUserId;

    /**
     * User groups for group-based task assignment.
     */
    private List<String> userGroups;

    /**
     * Filter tasks due before this time.
     */
    private Instant dueBefore;

    /**
     * Filter tasks created after this time.
     */
    private Instant createdAfter;

    /**
     * Filter tasks created before this time.
     */
    private Instant createdBefore;

    /**
     * Include only overdue tasks.
     */
    private Boolean overdueOnly;

    /**
     * Maximum number of results to return.
     */
    @Builder.Default
    private Integer limit = 100;

    /**
     * Offset for pagination.
     */
    @Builder.Default
    private Integer offset = 0;

    /**
     * Sort field.
     */
    @Builder.Default
    private String sortBy = "createdAt";

    /**
     * Sort direction (ASC or DESC).
     */
    @Builder.Default
    private String sortDirection = "DESC";
}
