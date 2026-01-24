package com.lyshra.open.app.core.engine.rest.dto;

import com.lyshra.open.app.integration.contract.humantask.ILyshraOpenAppHumanTask;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppHumanTaskStatus;
import com.lyshra.open.app.integration.enumerations.LyshraOpenAppHumanTaskType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Data Transfer Object for human task information.
 * Used for REST API responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HumanTaskDto {

    private String taskId;
    private String workflowInstanceId;
    private String workflowStepId;
    private String workflowDefinitionId;
    private LyshraOpenAppHumanTaskType taskType;
    private LyshraOpenAppHumanTaskStatus status;
    private String title;
    private String description;
    private int priority;
    private List<String> assignees;
    private List<String> candidateGroups;
    private String claimedBy;
    private Map<String, Object> taskData;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant dueAt;
    private Instant completedAt;
    private Map<String, Object> resultData;
    private String decisionReason;
    private String resolvedBy;
    private int commentCount;
    private Map<String, Object> metadata;

    /**
     * Creates a DTO from a human task entity.
     */
    public static HumanTaskDto fromEntity(ILyshraOpenAppHumanTask task) {
        if (task == null) {
            return null;
        }

        return HumanTaskDto.builder()
                .taskId(task.getTaskId())
                .workflowInstanceId(task.getWorkflowInstanceId())
                .workflowStepId(task.getWorkflowStepId())
                .workflowDefinitionId(task.getWorkflowDefinitionId().orElse(null))
                .taskType(task.getTaskType())
                .status(task.getStatus())
                .title(task.getTitle())
                .description(task.getDescription())
                .priority(task.getPriority())
                .assignees(task.getAssignees())
                .candidateGroups(task.getCandidateGroups())
                .claimedBy(task.getClaimedBy().orElse(null))
                .taskData(task.getTaskData())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .dueAt(task.getDueAt().orElse(null))
                .completedAt(task.getCompletedAt().orElse(null))
                .resultData(task.getResultData().orElse(null))
                .decisionReason(task.getDecisionReason().orElse(null))
                .resolvedBy(task.getResolvedBy().orElse(null))
                .commentCount(task.getComments() != null ? task.getComments().size() : 0)
                .metadata(task.getMetadata())
                .build();
    }
}
