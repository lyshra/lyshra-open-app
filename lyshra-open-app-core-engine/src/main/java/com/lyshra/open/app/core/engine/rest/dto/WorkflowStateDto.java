package com.lyshra.open.app.core.engine.rest.dto;

import com.lyshra.open.app.integration.enumerations.LyshraOpenAppWorkflowExecutionState;
import com.lyshra.open.app.integration.models.workflow.LyshraOpenAppWorkflowInstanceState;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * Data Transfer Object for workflow instance state.
 * Used for REST API responses.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowStateDto {

    private String instanceId;
    private String workflowDefinitionId;
    private LyshraOpenAppWorkflowExecutionState status;
    private String currentStepId;
    private String businessKey;
    private String tenantId;
    private String humanTaskId;
    private String suspensionReason;
    private Map<String, Object> variables;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant suspendedAt;
    private Instant scheduledResumeAt;
    private Instant completedAt;
    private String errorMessage;
    private boolean canResume;

    /**
     * Creates a DTO from a workflow instance state entity.
     */
    public static WorkflowStateDto fromEntity(LyshraOpenAppWorkflowInstanceState state) {
        if (state == null) {
            return null;
        }

        return WorkflowStateDto.builder()
                .instanceId(state.getInstanceId())
                .workflowDefinitionId(state.getWorkflowDefinitionId())
                .status(state.getStatus())
                .currentStepId(state.getCurrentStepId())
                .businessKey(state.getBusinessKey())
                .tenantId(state.getTenantId())
                .humanTaskId(state.getHumanTaskId())
                .suspensionReason(state.getSuspensionReason() != null ? state.getSuspensionReason().name() : null)
                .variables(state.getVariables())
                .createdAt(state.getCreatedAt())
                .updatedAt(state.getUpdatedAt())
                .suspendedAt(state.getSuspendedAt())
                .scheduledResumeAt(state.getScheduledResumeAt())
                .completedAt(state.getCompletedAt())
                .errorMessage(state.getErrorInfo() != null ? state.getErrorInfo().getErrorMessage() : null)
                .canResume(state.canResume())
                .build();
    }
}
